package pe.com.krypton.service.impl;

import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.request.ItemCarritoRequest;
import pe.com.krypton.dto.request.UpdateQuantityRequest;
import pe.com.krypton.dto.response.CarritoResponse;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.CarritoMapper;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.ItemCarrito;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.entity.Usuario;
import pe.com.krypton.repository.ItemCarritoRepository;
import pe.com.krypton.repository.CarritoRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.repository.UsuarioRepository;
import pe.com.krypton.service.CarritoService;

@Service
public class CarritoServiceImpl implements CarritoService {

    private final CarritoRepository cartRepository;
    private final ItemCarritoRepository cartItemRepository;
    private final ProductoRepository productRepository;
    private final UsuarioRepository userRepository;
    private final CarritoMapper cartMapper;
    private final CarritoService self;

    public CarritoServiceImpl(CarritoRepository cartRepository,
                           ItemCarritoRepository cartItemRepository,
                           ProductoRepository productRepository,
                           UsuarioRepository userRepository,
                           CarritoMapper cartMapper,
                           @Lazy CarritoService self) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cartMapper = cartMapper;
        this.self = self;
    }

    // ─── public API ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CarritoResponse getCart(String email) {
        Usuario user = resolveUser(email);
        return cartRepository.findByUser(user)
                .map(this::currentCart)
                .orElse(cartMapper.emptyCart());
    }

    /** Non-transactional orchestrator — catches constraint violation from tx1, retries in tx2. */
    @Override
    public CarritoResponse addItem(String email, ItemCarritoRequest request) {
        try {
            return self.attemptAddItem(email, request);
        } catch (DataIntegrityViolationException ex) {
            return self.mergeOnConflict(email, request);
        }
    }

    @Override
    @Transactional
    public CarritoResponse attemptAddItem(String email, ItemCarritoRequest request) {
        Usuario user = resolveUser(email);
        Carrito cart = getOrCreateCart(user);
        Producto product = resolveActiveProduct(request.productId());

        java.util.Optional<ItemCarrito> existing = cartItemRepository.findByCartAndProduct(cart, product);
        int finalQty = existing.map(i -> i.getQuantity() + request.quantity())
                .orElse(request.quantity());

        validateStock(product, finalQty);

        if (existing.isEmpty()) {
            ItemCarrito newItem = new ItemCarrito();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(request.quantity());
            cartItemRepository.saveAndFlush(newItem);  // surfaces UNIQUE violation synchronously
        } else {
            ItemCarrito item = existing.get();
            item.setQuantity(finalQty);
            cartItemRepository.save(item);
        }

        touch(cart);
        return currentCart(cart);
    }

    @Override
    @Transactional
    public CarritoResponse mergeOnConflict(String email, ItemCarritoRequest request) {
        Usuario user = resolveUser(email);
        Carrito cart = getOrCreateCart(user);
        Producto product = resolveActiveProduct(request.productId());

        java.util.Optional<ItemCarrito> existing = cartItemRepository.findByCartAndProduct(cart, product);
        int finalQty = existing.map(i -> i.getQuantity() + request.quantity())
                .orElse(request.quantity());

        validateStock(product, finalQty);

        if (existing.isPresent()) {
            ItemCarrito item = existing.get();
            item.setQuantity(finalQty);
            cartItemRepository.save(item);
        } else {
            // Defensive fallback: still absent — insert now (should not happen in normal flow)
            ItemCarrito newItem = new ItemCarrito();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(request.quantity());
            cartItemRepository.save(newItem);
        }

        touch(cart);
        return currentCart(cart);
    }

    @Override
    @Transactional
    public CarritoResponse updateItem(String email, Long itemId, UpdateQuantityRequest request) {
        Usuario user = resolveUser(email);
        ItemCarrito item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item no encontrado: " + itemId));
        requireOwnedItem(item, user);

        Producto product = resolveActiveProduct(item.getProduct().getId());
        validateStock(product, request.quantity());

        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        touch(item.getCart());
        return currentCart(item.getCart());
    }

    @Override
    @Transactional
    public void removeItem(String email, Long itemId) {
        Usuario user = resolveUser(email);
        ItemCarrito item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item no encontrado: " + itemId));
        requireOwnedItem(item, user);

        Carrito cart = item.getCart();
        cartItemRepository.delete(item);
        touch(cart);
    }

    @Override
    @Transactional
    public void clearCart(String email) {
        Usuario user = resolveUser(email);
        cartRepository.findByUser(user).ifPresent(cart -> {
            cartItemRepository.deleteByCart(cart);
            touch(cart);
        });
    }

    // ─── private helpers ─────────────────────────────────────────────────────────

    private Usuario resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + email));
    }

    /** Used by WRITE paths only — GET synthesizes an empty response without persisting. */
    private Carrito getOrCreateCart(Usuario user) {
        return cartRepository.findByUser(user).orElseGet(() -> {
            Carrito cart = new Carrito();
            cart.setUser(user);
            Instant now = Instant.now();
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);
            return cartRepository.saveAndFlush(cart);
        });
    }

    private Producto resolveActiveProduct(Long id) {
        Producto product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
        if (!product.isActive()) {
            throw new ResourceNotFoundException("Producto no disponible: " + id);
        }
        return product;
    }

    private void validateStock(Producto product, int qty) {
        if (qty > product.getStock()) {
            throw new InsufficientStockException(
                    "Stock insuficiente para el producto " + product.getId()
                    + ": solicitado=" + qty + ", disponible=" + product.getStock());
        }
    }

    private void touch(Carrito cart) {
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);
    }

    private ItemCarrito requireOwnedItem(ItemCarrito item, Usuario user) {
        if (!item.getCart().getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Item no encontrado: " + item.getId());
        }
        return item;
    }

    private CarritoResponse currentCart(Carrito cart) {
        List<ItemCarrito> items = cartItemRepository.findByCart(cart);
        return cartMapper.toResponse(cart, items);
    }
}
