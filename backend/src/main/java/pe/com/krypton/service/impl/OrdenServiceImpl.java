package pe.com.krypton.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.request.CheckoutRequest;
import pe.com.krypton.dto.request.PaymentRequest;
import pe.com.krypton.dto.response.OrdenResponse;
import pe.com.krypton.dto.response.PageResponse;
import pe.com.krypton.exception.ComprobanteNotAvailableException;
import pe.com.krypton.exception.EmptyCartException;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.exception.InvalidDocumentException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.OrdenMapper;
import pe.com.krypton.report.ComprobanteExporter;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.ItemCarrito;
import pe.com.krypton.entity.Orden;
import pe.com.krypton.entity.ItemOrden;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.entity.MovimientoStock;
import pe.com.krypton.entity.Usuario;
import pe.com.krypton.entity.enums.TipoDocumento;
import pe.com.krypton.entity.enums.TipoMovimiento;
import pe.com.krypton.entity.enums.EstadoOrden;
import pe.com.krypton.policy.EstadoOrdenPolicy;
import pe.com.krypton.repository.ItemCarritoRepository;
import pe.com.krypton.repository.CarritoRepository;
import pe.com.krypton.repository.ItemOrdenRepository;
import pe.com.krypton.repository.OrdenRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.repository.MovimientoStockRepository;
import pe.com.krypton.repository.UsuarioRepository;
import pe.com.krypton.service.CarritoService;
import pe.com.krypton.service.OrdenService;
import pe.com.krypton.spec.OrdenSpecification;

@Service
public class OrdenServiceImpl extends ICRUDImpl<Orden, Long> implements OrdenService {

    // ── Reglas de negocio de facturación ──
    /** Envío gratis si el subtotal alcanza este umbral. */
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("300.00");
    /** Costo de envío fijo cuando no aplica el envío gratis. */
    private static final BigDecimal SHIPPING_COST = new BigDecimal("20.00");
    /** 1 + tasa IGV (18%). El precio ya incluye IGV → se desglosa dividiendo por esto. */
    private static final BigDecimal IGV_DIVISOR = new BigDecimal("1.18");

    private final OrdenRepository orderRepository;
    private final ItemOrdenRepository orderItemRepository;
    private final ProductoRepository productRepository;
    private final MovimientoStockRepository stockMovementRepository;
    private final CarritoRepository cartRepository;
    private final ItemCarritoRepository cartItemRepository;
    private final UsuarioRepository userRepository;
    private final CarritoService cartService;
    private final OrdenMapper orderMapper;
    private final EstadoOrdenPolicy statusPolicy;
    private final ComprobanteExporter comprobanteExporter;

    public OrdenServiceImpl(OrdenRepository orderRepository,
                            ItemOrdenRepository orderItemRepository,
                            ProductoRepository productRepository,
                            MovimientoStockRepository stockMovementRepository,
                            CarritoRepository cartRepository,
                            ItemCarritoRepository cartItemRepository,
                            UsuarioRepository userRepository,
                            CarritoService cartService,
                            OrdenMapper orderMapper,
                            EstadoOrdenPolicy statusPolicy,
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

    /** Repository que usa el CRUD genérico heredado (guardar/listarTodos/...). */
    @Override
    protected JpaRepository<Orden, Long> repo() {
        return orderRepository;
    }

    // ─── CLIENT: checkout ────────────────────────────────────────────────────────

    /**
     * Atomic checkout — single @Transactional boundary.
     * Two-pass validate-then-mutate (ADR-1):
     *   Pass A: PESSIMISTIC_WRITE lock on each product → validate stock → accumulate total.
     *   Pass B: save Orden → per-item: save ItemOrden (price snapshot), decrement stock,
     *           save MovimientoStock(SALIDA) → clearCart.
     */
    @Override
    @Transactional
    public OrdenResponse confirmarCompra(String email, CheckoutRequest request) {
        validateDocument(request);
        Usuario user = resolveUser(email);

        // Resolve cart — no cart or empty cart → 400
        Carrito cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new EmptyCartException("El carrito está vacío"));
        List<ItemCarrito> cartItems = cartItemRepository.findByCart(cart);
        if (cartItems.isEmpty()) {
            throw new EmptyCartException("El carrito está vacío");
        }

        // ── Pass A: validate all products + accumulate subtotal (productos, IGV incl.) ──
        List<Producto> lockedProducts = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (ItemCarrito ci : cartItems) {
            Long productId = ci.getProduct().getId();
            Producto product = productRepository.findByIdWithLock(productId)
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

        // ── Pass B: persist Orden then each line ─────────────────────────────────
        Orden order = buildOrder(user, request, subtotal, shippingCost, igv, total);
        Orden savedOrder = guardar(order);   // ← heredado de ICRUDImpl

        List<ItemOrden> savedItems = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            ItemCarrito ci = cartItems.get(i);
            Producto product = lockedProducts.get(i);
            int qty = ci.getQuantity();

            // ItemOrden with unit_price snapshot
            ItemOrden oi = new ItemOrden();
            oi.setOrder(savedOrder);
            oi.setProduct(product);
            oi.setQuantity(qty);
            oi.setUnitPrice(product.getPrice()); // snapshot at checkout time
            savedItems.add(orderItemRepository.save(oi));

            // Decrement stock
            product.setStock(product.getStock() - qty);
            productRepository.save(product);

            // MovimientoStock (SALIDA)
            MovimientoStock movement = new MovimientoStock();
            movement.setProduct(product);
            movement.setType(TipoMovimiento.SALIDA);
            movement.setQuantity(qty);
            movement.setReason("Venta orden #" + savedOrder.getId());
            movement.setReference("ORDER-" + savedOrder.getId());
            movement.setCreatedAt(Instant.now());
            movement.setCreatedBy(null);
            stockMovementRepository.save(movement);
        }

        // Clear cart (joins this tx via PROPAGATION.REQUIRED)
        cartService.vaciarCarrito(email);

        return orderMapper.toResponse(savedOrder, savedItems);
    }

    // ─── CLIENT: read ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<OrdenResponse> misOrdenes(String email) {
        Usuario user = resolveUser(email);
        List<Orden> orders = orderRepository.findByUserOrderByOrderDateDesc(user);
        return orders.stream()
                .map(o -> orderMapper.toResponse(o, orderItemRepository.findByOrder(o)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrdenResponse miOrden(String email, Long orderId) {
        Usuario user = resolveUser(email);
        Orden order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada: " + orderId));
        return orderMapper.toResponse(order, orderItemRepository.findByOrder(order));
    }

    // ─── CLIENT: pay ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrdenResponse pagar(String email, Long orderId, PaymentRequest request) {
        Usuario user = resolveUser(email);
        Orden order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada: " + orderId));
        // Pago = transición PENDIENTE → CONFIRMADA. La guarda la aplica statusPolicy.
        // El método de pago (simulado) queda persistido en el pedido.
        order.setPaymentMethod(request.method());
        transitionTo(order, EstadoOrden.CONFIRMADA);
        return orderMapper.toResponse(order, orderItemRepository.findByOrder(order));
    }

    // ─── CLIENT / ADMIN: comprobante PDF ──────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public byte[] miComprobantePdf(String email, Long orderId) {
        Usuario user = resolveUser(email);
        Orden order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada: " + orderId));
        return renderComprobante(order);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] comprobantePdf(Long orderId) {
        Orden order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada: " + orderId));
        return renderComprobante(order);
    }

    /** El comprobante sólo se emite para pedidos pagados (no PENDIENTE ni CANCELADA). */
    private byte[] renderComprobante(Orden order) {
        EstadoOrden status = order.getStatus();
        if (status == EstadoOrden.PENDIENTE || status == EstadoOrden.CANCELADA) {
            throw new ComprobanteNotAvailableException(
                    "El comprobante sólo está disponible para pedidos pagados.");
        }
        return comprobanteExporter.export(order, orderItemRepository.findByOrder(order));
    }

    // ─── ADMIN ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrdenResponse> listarOrdenes(EstadoOrden status, Instant from, Instant to, Pageable pageable) {
        // Compone los filtros opcionales (null = ausente, gracias al contrato de OrdenSpecification).
        Specification<Orden> spec = Specification
                .where(OrdenSpecification.hasStatus(status))
                .and(OrdenSpecification.dateBetween(from, to));
        Page<OrdenResponse> responsePage = orderRepository.findAll(spec, pageable)
                .map(o -> orderMapper.toResponse(o, orderItemRepository.findByOrder(o)));
        return PageResponse.of(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public OrdenResponse obtenerOrden(Long orderId) {
        Orden order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Orden no encontrada: " + orderId));
        return orderMapper.toResponse(order, orderItemRepository.findByOrder(order));
    }

    @Override
    @Transactional
    public OrdenResponse actualizarEstado(Long orderId, EstadoOrden newStatus) {
        Orden order = orderRepository.findById(orderId)
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
    private void transitionTo(Orden order, EstadoOrden newStatus) {
        statusPolicy.assertCanTransition(order.getStatus(), newStatus);
        if (newStatus == EstadoOrden.CANCELADA) {
            revertStock(order);
        }
        order.setStatus(newStatus);
        guardar(order);   // ← heredado de ICRUDImpl
    }

    /**
     * Reposición de stock al cancelar — espejo inverso del SALIDA del checkout.
     * El stock se descontó en checkout (estado PENDIENTE), así que CUALQUIER
     * cancelación debe devolverlo: incrementa products.stock y registra un
     * MovimientoStock(ENTRADA) por cada ítem, manteniendo stock cacheado y kardex
     * consistentes. Bloquea cada producto (PESSIMISTIC_WRITE) igual que el checkout.
     */
    private void revertStock(Orden order) {
        for (ItemOrden item : orderItemRepository.findByOrder(order)) {
            Long productId = item.getProduct().getId();
            Producto product = productRepository.findByIdWithLock(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado: " + productId));
            product.setStock(product.getStock() + item.getQuantity());
            productRepository.save(product);

            MovimientoStock movement = new MovimientoStock();
            movement.setProduct(product);
            movement.setType(TipoMovimiento.ENTRADA);
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
        if (request.documentType() == TipoDocumento.FACTURA && doc.length() != 11) {
            throw new InvalidDocumentException("La factura requiere un RUC de 11 dígitos");
        }
        if (request.documentType() == TipoDocumento.BOLETA && doc.length() != 8) {
            throw new InvalidDocumentException("La boleta requiere un DNI de 8 dígitos");
        }
    }

    private Usuario resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + email));
    }

    private Orden buildOrder(Usuario user, CheckoutRequest request, BigDecimal subtotal,
                             BigDecimal shippingCost, BigDecimal igv, BigDecimal total) {
        Orden o = new Orden();
        o.setUser(user);
        o.setOrderDate(Instant.now());
        o.setStatus(EstadoOrden.PENDIENTE);
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
