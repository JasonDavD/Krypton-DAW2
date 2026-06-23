package pe.com.krypton.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.request.CheckoutRequest;
import pe.com.krypton.dto.request.PaymentRequest;
import pe.com.krypton.dto.response.OrderResponse;
import pe.com.krypton.dto.response.PageResponse;
import pe.com.krypton.exception.ComprobanteNotAvailableException;
import pe.com.krypton.exception.EmptyCartException;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.exception.InvalidDocumentException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.OrderMapper;
import pe.com.krypton.report.ComprobanteExporter;
import pe.com.krypton.model.Cart;
import pe.com.krypton.model.CartItem;
import pe.com.krypton.model.Order;
import pe.com.krypton.model.OrderItem;
import pe.com.krypton.model.Product;
import pe.com.krypton.model.StockMovement;
import pe.com.krypton.model.User;
import pe.com.krypton.model.enums.DocumentType;
import pe.com.krypton.model.enums.MovementType;
import pe.com.krypton.model.enums.OrderStatus;
import pe.com.krypton.policy.OrderStatusPolicy;
import pe.com.krypton.repository.CartItemRepository;
import pe.com.krypton.repository.CartRepository;
import pe.com.krypton.repository.OrderItemRepository;
import pe.com.krypton.repository.OrderRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.repository.StockMovementRepository;
import pe.com.krypton.repository.UserRepository;
import pe.com.krypton.service.CartService;
import pe.com.krypton.service.OrderService;
import pe.com.krypton.spec.OrderSpecification;

@Service
public class OrderServiceImpl implements OrderService {

    // ── Reglas de negocio de facturación ──
    /** Envío gratis si el subtotal alcanza este umbral. */
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("300.00");
    /** Costo de envío fijo cuando no aplica el envío gratis. */
    private static final BigDecimal SHIPPING_COST = new BigDecimal("20.00");
    /** 1 + tasa IGV (18%). El precio ya incluye IGV → se desglosa dividiendo por esto. */
    private static final BigDecimal IGV_DIVISOR = new BigDecimal("1.18");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final OrderMapper orderMapper;
    private final OrderStatusPolicy statusPolicy;
    private final ComprobanteExporter comprobanteExporter;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            ProductRepository productRepository,
                            StockMovementRepository stockMovementRepository,
                            CartRepository cartRepository,
                            CartItemRepository cartItemRepository,
                            UserRepository userRepository,
                            CartService cartService,
                            OrderMapper orderMapper,
                            OrderStatusPolicy statusPolicy,
                            ComprobanteExporter comprobanteExporter) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.cartService = cartService;
        this.orderMapper = orderMapper;
        this.statusPolicy = statusPolicy;
        this.comprobanteExporter = comprobanteExporter;
    }

    // ─── CLIENT: checkout ────────────────────────────────────────────────────────

    /**
     * Atomic checkout — single @Transactional boundary.
     * Two-pass validate-then-mutate (ADR-1):
     *   Pass A: PESSIMISTIC_WRITE lock on each product → validate stock → accumulate total.
     *   Pass B: save Order → per-item: save OrderItem (price snapshot), decrement stock,
     *           save StockMovement(SALIDA) → clearCart.
     */
    @Override
    @Transactional
    public OrderResponse checkout(String email, CheckoutRequest request) {
        validateDocument(request);
        User user = resolveUser(email);

        // Resolve cart — no cart or empty cart → 400
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new EmptyCartException("El carrito está vacío"));
        List<CartItem> cartItems = cartItemRepository.findByCart(cart);
        if (cartItems.isEmpty()) {
            throw new EmptyCartException("El carrito está vacío");
        }

        // ── Pass A: validate all products + accumulate subtotal (productos, IGV incl.) ──
        List<Product> lockedProducts = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem ci : cartItems) {
            Long productId = ci.getProduct().getId();
            Product product = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado: " + productId));
            int requested = ci.getQuantity();
            if (requested > product.getStock()) {
                throw new InsufficientStockException(
                        "Stock insuficiente para el producto " + productId
                        + ": solicitado=" + requested + ", disponible=" + product.getStock());
            }
            lockedProducts.add(product);
            subtotal = subtotal.add(product.getPrice().multiply(BigDecimal.valueOf(requested)));
        }

        // ── Montos: envío + total + IGV desglosado hacia adentro ─────────────────
        BigDecimal shippingCost = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO
                : SHIPPING_COST;
        BigDecimal total = subtotal.add(shippingCost);
        // El precio ya incluye IGV: base = total / 1.18 (redondeada), igv = total − base.
        // Restar garantiza base + igv == total exacto (sin descuadres de centavos).
        BigDecimal base = total.divide(IGV_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(base);

        // ── Pass B: persist Order then each line ─────────────────────────────────
        Order order = buildOrder(user, request, subtotal, shippingCost, igv, total);
        Order savedOrder = orderRepository.save(order);

        List<OrderItem> savedItems = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem ci = cartItems.get(i);
            Product product = lockedProducts.get(i);
            int qty = ci.getQuantity();

            // OrderItem with unit_price snapshot
            OrderItem oi = new OrderItem();
            oi.setOrder(savedOrder);
            oi.setProduct(product);
            oi.setQuantity(qty);
            oi.setUnitPrice(product.getPrice()); // snapshot at checkout time
            savedItems.add(orderItemRepository.save(oi));

            // Decrement stock
            product.setStock(product.getStock() - qty);
            productRepository.save(product);

            // StockMovement (SALIDA)
            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setType(MovementType.SALIDA);
            movement.setQuantity(qty);
            movement.setReason("Venta orden #" + savedOrder.getId());
            movement.setReference("ORDER-" + savedOrder.getId());
            movement.setCreatedAt(Instant.now());
            movement.setCreatedBy(null);
            stockMovementRepository.save(movement);
        }

        // Clear cart (joins this tx via PROPAGATION.REQUIRED)
        cartService.clearCart(email);

        return orderMapper.toResponse(savedOrder, savedItems);
    }

    // ─── CLIENT: read ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String email) {
        User user = resolveUser(email);
        List<Order> orders = orderRepository.findByUserOrderByOrderDateDesc(user);
        return orders.stream()
                .map(o -> orderMapper.toResponse(o, orderItemRepository.findByOrder(o)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(String email, Long orderId) {
        User user = resolveUser(email);
        Order order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada: " + orderId));
        return orderMapper.toResponse(order, orderItemRepository.findByOrder(order));
    }

    // ─── CLIENT: pay ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrderResponse pay(String email, Long orderId, PaymentRequest request) {
        User user = resolveUser(email);
        Order order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada: " + orderId));
        // Pago = transición PENDIENTE → CONFIRMADA. La guarda la aplica statusPolicy.
        // El método de pago (simulado) queda persistido en el pedido.
        order.setPaymentMethod(request.method());
        transitionTo(order, OrderStatus.CONFIRMADA);
        return orderMapper.toResponse(order, orderItemRepository.findByOrder(order));
    }

    // ─── CLIENT / ADMIN: comprobante PDF ──────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public byte[] getMyComprobantePdf(String email, Long orderId) {
        User user = resolveUser(email);
        Order order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada: " + orderId));
        return renderComprobante(order);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getComprobantePdf(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada: " + orderId));
        return renderComprobante(order);
    }

    /** El comprobante sólo se emite para pedidos pagados (no PENDIENTE ni CANCELADA). */
    private byte[] renderComprobante(Order order) {
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.PENDIENTE || status == OrderStatus.CANCELADA) {
            throw new ComprobanteNotAvailableException(
                    "El comprobante sólo está disponible para pedidos pagados.");
        }
        return comprobanteExporter.export(order, orderItemRepository.findByOrder(order));
    }

    // ─── ADMIN ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrders(OrderStatus status, Instant from, Instant to, Pageable pageable) {
        // Compone los filtros opcionales (null = ausente, gracias al contrato de OrderSpecification).
        Specification<Order> spec = Specification
                .where(OrderSpecification.hasStatus(status))
                .and(OrderSpecification.dateBetween(from, to));
        Page<OrderResponse> responsePage = orderRepository.findAll(spec, pageable)
                .map(o -> orderMapper.toResponse(o, orderItemRepository.findByOrder(o)));
        return PageResponse.of(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada: " + orderId));
        return orderMapper.toResponse(order, orderItemRepository.findByOrder(order));
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada: " + orderId));
        transitionTo(order, newStatus);
        return orderMapper.toResponse(order, orderItemRepository.findByOrder(order));
    }

    // ─── private helpers ─────────────────────────────────────────────────────────

    /**
     * Único punto de cambio de estado. Valida la transición contra la máquina de
     * estados (statusPolicy) y, si el destino es CANCELADA, repone el stock.
     * El efecto secundario se orquesta aquí —no en la policy— por separación de
     * responsabilidades: la policy solo decide "¿es legal?".
     */
    private void transitionTo(Order order, OrderStatus newStatus) {
        statusPolicy.assertCanTransition(order.getStatus(), newStatus);
        if (newStatus == OrderStatus.CANCELADA) {
            revertStock(order);
        }
        order.setStatus(newStatus);
        orderRepository.save(order);
    }

    /**
     * Reposición de stock al cancelar — espejo inverso del SALIDA del checkout.
     * El stock se descontó en checkout (estado PENDIENTE), así que CUALQUIER
     * cancelación debe devolverlo: incrementa products.stock y registra un
     * StockMovement(ENTRADA) por cada ítem, manteniendo stock cacheado y kardex
     * consistentes. Bloquea cada producto (PESSIMISTIC_WRITE) igual que el checkout.
     */
    private void revertStock(Order order) {
        for (OrderItem item : orderItemRepository.findByOrder(order)) {
            Long productId = item.getProduct().getId();
            Product product = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado: " + productId));
            product.setStock(product.getStock() + item.getQuantity());
            productRepository.save(product);

            StockMovement movement = new StockMovement();
            movement.setProduct(product);
            movement.setType(MovementType.ENTRADA);
            movement.setQuantity(item.getQuantity());
            movement.setReason("Cancelación orden #" + order.getId());
            movement.setReference("ORDER-" + order.getId());
            movement.setCreatedAt(Instant.now());
            movement.setCreatedBy(null);
            stockMovementRepository.save(movement);
        }
    }

    /**
     * Regla condicional del comprobante: FACTURA exige RUC (11 díg), BOLETA exige DNI
     * (8 díg). El @Pattern del DTO sólo garantiza 8 u 11 genérico; acá se valida la
     * combinación con el tipo. Falla rápido (antes de tocar usuario/carrito) → 422.
     */
    private void validateDocument(CheckoutRequest request) {
        String doc = request.customerDoc();
        if (request.documentType() == DocumentType.FACTURA && doc.length() != 11) {
            throw new InvalidDocumentException("La factura requiere un RUC de 11 dígitos");
        }
        if (request.documentType() == DocumentType.BOLETA && doc.length() != 8) {
            throw new InvalidDocumentException("La boleta requiere un DNI de 8 dígitos");
        }
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + email));
    }

    private Order buildOrder(User user, CheckoutRequest request, BigDecimal subtotal,
                             BigDecimal shippingCost, BigDecimal igv, BigDecimal total) {
        Order o = new Order();
        o.setUser(user);
        o.setOrderDate(Instant.now());
        o.setStatus(OrderStatus.PENDIENTE);
        o.setDocumentType(request.documentType());
        o.setCustomerName(request.customerName());
        o.setCustomerDoc(request.customerDoc());
        o.setSubtotal(subtotal);
        o.setShippingCost(shippingCost);
        o.setIgv(igv);
        o.setTotal(total);
        return o;
    }
}
