package pe.com.krypton.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.client.CatalogClient;
import pe.com.krypton.client.dto.ProductoResponse;
import pe.com.krypton.dto.request.ItemCarritoRequest;
import pe.com.krypton.dto.request.UpdateQuantityRequest;
import pe.com.krypton.dto.response.CarritoResponse;
import pe.com.krypton.dto.response.ItemCarritoResponse;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.ItemCarrito;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.CarritoMapper;
import pe.com.krypton.repository.CarritoRepository;
import pe.com.krypton.repository.ItemCarritoRepository;
import pe.com.krypton.service.CarritoService;

/**
 * Carrito del usuario. ADAPTACION microservicios: los datos del producto (nombre, sku,
 * precio, stock) NO viven acá — se consultan VIVOS a catalog-service vía Feign.
 */
@Service
public class CarritoServiceImpl implements CarritoService {

    private final CarritoRepository cartRepository;
    private final ItemCarritoRepository cartItemRepository;
    private final CarritoMapper cartMapper;
    private final CatalogClient catalogClient;

    public CarritoServiceImpl(CarritoRepository cartRepository,
                              ItemCarritoRepository cartItemRepository,
                              CarritoMapper cartMapper,
                              CatalogClient catalogClient) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartMapper = cartMapper;
        this.catalogClient = catalogClient;
    }

    // ─── public API ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CarritoResponse obtenerCarrito(String email) {
        return cartRepository.findByUserEmail(email)
                .map(this::currentCart)
                .orElseGet(cartMapper::emptyCart);
    }

    @Override
    @Transactional
    public CarritoResponse agregarItem(String email, ItemCarritoRequest request) {
        Carrito cart = getOrCreateCart(email);
        ProductoResponse product = catalogClient.getProduct(request.productId());
        if (!product.active()) {
            throw new ResourceNotFoundException("Producto no disponible: " + request.productId());
        }

        Optional<ItemCarrito> existing = cartItemRepository.findByCartAndProductId(cart, request.productId());
        int finalQty = existing.map(i -> i.getQuantity() + request.quantity())
                .orElse(request.quantity());
        validateStock(product, finalQty);

        ItemCarrito item = existing.orElseGet(() -> {
            ItemCarrito nuevo = new ItemCarrito();
            nuevo.setCart(cart);
            nuevo.setProductId(request.productId());
            return nuevo;
        });
        item.setQuantity(finalQty);
        cartItemRepository.save(item);

        touch(cart);
        return obtenerCarrito(email);
    }

    @Override
    @Transactional
    public CarritoResponse actualizarItem(String email, Long itemId, UpdateQuantityRequest request) {
        Carrito cart = resolveCart(email);
        ItemCarrito item = requireOwnedItem(cart, itemId);

        ProductoResponse product = catalogClient.getProduct(item.getProductId());
        validateStock(product, request.quantity());

        item.setQuantity(request.quantity());
        cartItemRepository.save(item);

        touch(cart);
        return obtenerCarrito(email);
    }

    @Override
    @Transactional
    public void quitarItem(String email, Long itemId) {
        Carrito cart = resolveCart(email);
        ItemCarrito item = requireOwnedItem(cart, itemId);
        cartItemRepository.delete(item);
        touch(cart);
    }

    @Override
    @Transactional
    public void vaciarCarrito(String email) {
        cartRepository.findByUserEmail(email).ifPresent(cart -> {
            cartItemRepository.deleteByCart(cart);
            touch(cart);
        });
    }

    // ─── private helpers ─────────────────────────────────────────────────────────

    /** Usado SOLO por rutas de escritura: crea el carrito si no existe. */
    private Carrito getOrCreateCart(String email) {
        return cartRepository.findByUserEmail(email).orElseGet(() -> {
            Carrito cart = new Carrito();
            cart.setUserEmail(email);
            Instant now = Instant.now();
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);
            return cartRepository.save(cart);
        });
    }

    /** Carrito del usuario o 404 (para operar sobre items, el carrito debe existir). */
    private Carrito resolveCart(String email) {
        return cartRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Carrito no encontrado para: " + email));
    }

    /** Item por id que pertenece a este carrito; si no, 404 (evita IDOR). */
    private ItemCarrito requireOwnedItem(Carrito cart, Long itemId) {
        ItemCarrito item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item no encontrado: " + itemId));
        if (!item.getCart().getId().equals(cart.getId())) {
            throw new ResourceNotFoundException("Item no encontrado: " + itemId);
        }
        return item;
    }

    private void validateStock(ProductoResponse product, int qty) {
        if (qty > product.stock()) {
            throw new InsufficientStockException(
                    "Stock insuficiente para el producto " + product.id()
                            + ": solicitado=" + qty + ", disponible=" + product.stock());
        }
    }

    private void touch(Carrito cart) {
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);
    }

    /** Arma la respuesta del carrito consultando el producto vivo de cada línea. */
    private CarritoResponse currentCart(Carrito cart) {
        List<ItemCarrito> items = cartItemRepository.findByCart(cart);
        List<ItemCarritoResponse> itemResponses = items.stream()
                .map(item -> {
                    ProductoResponse product = catalogClient.getProduct(item.getProductId());
                    return cartMapper.toItemResponse(item, product);
                })
                .toList();
        return cartMapper.toResponse(cart, itemResponses);
    }
}
