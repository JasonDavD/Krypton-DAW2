package pe.com.krypton.service.impl;

import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.request.CartItemRequest;
import pe.com.krypton.dto.request.UpdateQuantityRequest;
import pe.com.krypton.dto.response.CartResponse;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.CartMapper;
import pe.com.krypton.model.Cart;
import pe.com.krypton.model.CartItem;
import pe.com.krypton.model.Product;
import pe.com.krypton.model.User;
import pe.com.krypton.repository.CartItemRepository;
import pe.com.krypton.repository.CartRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.repository.UserRepository;
import pe.com.krypton.service.CartService;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;
    private final CartService self;

    public CartServiceImpl(CartRepository cartRepository,
                           CartItemRepository cartItemRepository,
                           ProductRepository productRepository,
                           UserRepository userRepository,
                           CartMapper cartMapper,
                           @Lazy CartService self) {
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
    public CartResponse getCart(String email) {
        User user = resolveUser(email);
        return cartRepository.findByUser(user)
                .map(this::currentCart)
                .orElse(cartMapper.emptyCart());
    }

    /** Non-transactional orchestrator — catches constraint violation from tx1, retries in tx2. */
    @Override
    public CartResponse addItem(String email, CartItemRequest request) {
        try {
            return self.attemptAddItem(email, request);
        } catch (DataIntegrityViolationException ex) {
            return self.mergeOnConflict(email, request);
        }
    }

    @Override
    @Transactional
    public CartResponse attemptAddItem(String email, CartItemRequest request) {
        User user = resolveUser(email);
        Cart cart = getOrCreateCart(user);
        Product product = resolveActiveProduct(request.productId());

        java.util.Optional<CartItem> existing = cartItemRepository.findByCartAndProduct(cart, product);
        int finalQty = existing.map(i -> i.getQuantity() + request.quantity())
                .orElse(request.quantity());

        validateStock(product, finalQty);

        if (existing.isEmpty()) {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(request.quantity());
            cartItemRepository.saveAndFlush(newItem);  // surfaces UNIQUE violation synchronously
        } else {
            CartItem item = existing.get();
            item.setQuantity(finalQty);
            cartItemRepository.save(item);
        }

        touch(cart);
        return currentCart(cart);
    }

    @Override
    @Transactional
    public CartResponse mergeOnConflict(String email, CartItemRequest request) {
        User user = resolveUser(email);
        Cart cart = getOrCreateCart(user);
        Product product = resolveActiveProduct(request.productId());

        java.util.Optional<CartItem> existing = cartItemRepository.findByCartAndProduct(cart, product);
        int finalQty = existing.map(i -> i.getQuantity() + request.quantity())
                .orElse(request.quantity());

        validateStock(product, finalQty);

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(finalQty);
            cartItemRepository.save(item);
        } else {
            // Defensive fallback: still absent — insert now (should not happen in normal flow)
            CartItem newItem = new CartItem();
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
    public CartResponse updateItem(String email, Long itemId, UpdateQuantityRequest request) {
        User user = resolveUser(email);
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item no encontrado: " + itemId));
        requireOwnedItem(item, user);

        Product product = resolveActiveProduct(item.getProduct().getId());
        validateStock(product, request.quantity());

        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        touch(item.getCart());
        return currentCart(item.getCart());
    }

    @Override
    @Transactional
    public void removeItem(String email, Long itemId) {
        User user = resolveUser(email);
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item no encontrado: " + itemId));
        requireOwnedItem(item, user);

        Cart cart = item.getCart();
        cartItemRepository.delete(item);
        touch(cart);
    }

    @Override
    @Transactional
    public void clearCart(String email) {
        User user = resolveUser(email);
        cartRepository.findByUser(user).ifPresent(cart -> {
            cartItemRepository.deleteByCart(cart);
            touch(cart);
        });
    }

    // ─── private helpers ─────────────────────────────────────────────────────────

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + email));
    }

    /** Used by WRITE paths only — GET synthesizes an empty response without persisting. */
    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUser(user).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(user);
            Instant now = Instant.now();
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);
            return cartRepository.saveAndFlush(cart);
        });
    }

    private Product resolveActiveProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
        if (!product.isActive()) {
            throw new ResourceNotFoundException("Producto no disponible: " + id);
        }
        return product;
    }

    private void validateStock(Product product, int qty) {
        if (qty > product.getStock()) {
            throw new InsufficientStockException(
                    "Stock insuficiente para el producto " + product.getId()
                    + ": solicitado=" + qty + ", disponible=" + product.getStock());
        }
    }

    private void touch(Cart cart) {
        cart.setUpdatedAt(Instant.now());
        cartRepository.save(cart);
    }

    private CartItem requireOwnedItem(CartItem item, User user) {
        if (!item.getCart().getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Item no encontrado: " + item.getId());
        }
        return item;
    }

    private CartResponse currentCart(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCart(cart);
        return cartMapper.toResponse(cart, items);
    }
}
