package pe.com.krypton.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import pe.com.krypton.dto.request.CheckoutRequest;
import pe.com.krypton.dto.request.PaymentRequest;
import pe.com.krypton.dto.response.OrdenResponse;
import pe.com.krypton.dto.response.PageResponse;
import pe.com.krypton.exception.ComprobanteNotAvailableException;
import pe.com.krypton.exception.EmptyCartException;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.exception.InvalidDocumentException;
import pe.com.krypton.exception.OrderStatusTransitionException;
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
import pe.com.krypton.entity.enums.MetodoPago;
import pe.com.krypton.policy.EstadoOrdenPolicy;
import pe.com.krypton.repository.ItemCarritoRepository;
import pe.com.krypton.repository.CarritoRepository;
import pe.com.krypton.repository.ItemOrdenRepository;
import pe.com.krypton.repository.OrdenRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.repository.MovimientoStockRepository;
import pe.com.krypton.repository.UsuarioRepository;
import pe.com.krypton.service.impl.OrdenServiceImpl;

/**
 * Unit test for OrdenServiceImpl. All repos + CarritoService + OrdenMapper mocked.
 * Strict TDD: RED → GREEN → REFACTOR per group.
 * Satisfies REQ-OM-01..REQ-OM-12 / ADR-1..ADR-9 / ADR-10.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock OrdenRepository orderRepository;
    @Mock ItemOrdenRepository orderItemRepository;
    @Mock ProductoRepository productRepository;
    @Mock MovimientoStockRepository stockMovementRepository;
    @Mock CarritoRepository cartRepository;
    @Mock ItemCarritoRepository cartItemRepository;
    @Mock UsuarioRepository userRepository;
    @Mock CarritoService cartService;
    @Mock OrdenMapper orderMapper;
    @Mock ComprobanteExporter comprobanteExporter;

    OrdenServiceImpl service;

    // Pure domain rule — real instance, not a mock (no I/O, like a value object).
    final EstadoOrdenPolicy statusPolicy = new EstadoOrdenPolicy();

    @BeforeEach
    void setUp() {
        service = new OrdenServiceImpl(
                orderRepository, orderItemRepository, productRepository,
                stockMovementRepository, cartRepository, cartItemRepository,
                userRepository, cartService, orderMapper, statusPolicy,
                comprobanteExporter);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Usuario user(Long id, String email) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private Carrito cart(Long id, Usuario user) {
        Carrito c = new Carrito();
        c.setId(id);
        c.setUser(user);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        return c;
    }

    private Producto product(Long id, String name, BigDecimal price, int stock) {
        Producto p = new Producto();
        p.setId(id);
        p.setName(name);
        p.setPrice(price);
        p.setStock(stock);
        p.setActive(true);
        return p;
    }

    private ItemCarrito cartItem(Long id, Carrito cart, Producto product, int qty) {
        ItemCarrito ci = new ItemCarrito();
        ci.setId(id);
        ci.setCart(cart);
        ci.setProduct(product);
        ci.setQuantity(qty);
        return ci;
    }

    private Orden order(Long id, Usuario user, BigDecimal total, EstadoOrden status) {
        Orden o = new Orden();
        o.setId(id);
        o.setUser(user);
        o.setTotal(total);
        o.setStatus(status);
        o.setOrderDate(Instant.now());
        return o;
    }

    private ItemOrden orderItem(Long id, Orden order, Producto product, int qty, BigDecimal unitPrice) {
        ItemOrden oi = new ItemOrden();
        oi.setId(id);
        oi.setOrder(order);
        oi.setProduct(product);
        oi.setQuantity(qty);
        oi.setUnitPrice(unitPrice);
        return oi;
    }

    private OrdenResponse sampleResponse() {
        return new OrdenResponse(1L, 3L, Instant.now(), "PENDIENTE",
                "BOLETA", "Juan Cliente", "12345678",
                new BigDecimal("299.90"), BigDecimal.ZERO, new BigDecimal("45.75"),
                new BigDecimal("299.90"), List.of());
    }

    /** Comprobante por defecto para checkout (boleta a consumidor final). */
    private CheckoutRequest boletaRequest() {
        return new CheckoutRequest(TipoDocumento.BOLETA, "Juan Cliente", "12345678");
    }

    // ─── CHECKOUT GROUP ─────────────────────────────────────────────────────────

    @Test
    void checkout_happy_path_creates_order_items_movements_and_clears_cart() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Carrito c = cart(1L, u);
        Producto p = product(10L, "Notebook", new BigDecimal("299.90"), 5);
        ItemCarrito ci = cartItem(1L, c, p, 2);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of(ci));
        when(productRepository.findByIdWithLock(10L)).thenReturn(Optional.of(p));

        // Orden saved returns an order with id=1
        Orden savedOrder = order(1L, u, new BigDecimal("599.80"), EstadoOrden.PENDIENTE);
        when(orderRepository.save(any(Orden.class))).thenReturn(savedOrder);

        ItemOrden savedOrderItem = orderItem(1L, savedOrder, p, 2, new BigDecimal("299.90"));
        when(orderItemRepository.save(any(ItemOrden.class))).thenReturn(savedOrderItem);
        when(stockMovementRepository.save(any(MovimientoStock.class))).thenReturn(new MovimientoStock());

        OrdenResponse expectedResponse = sampleResponse();
        when(orderMapper.toResponse(eq(savedOrder), any())).thenReturn(expectedResponse);

        OrdenResponse result = service.confirmarCompra(email, boletaRequest());

        assertThat(result).isNotNull();

        // Verify order saved with PENDIENTE, comprobante y desglose correctos
        ArgumentCaptor<Orden> orderCaptor = ArgumentCaptor.forClass(Orden.class);
        verify(orderRepository).save(orderCaptor.capture());
        Orden capturedOrder = orderCaptor.getValue();
        assertThat(capturedOrder.getStatus()).isEqualTo(EstadoOrden.PENDIENTE);
        // subtotal 599.80 ≥ 300 → envío gratis; total = subtotal
        assertThat(capturedOrder.getSubtotal()).isEqualByComparingTo(new BigDecimal("599.80"));
        assertThat(capturedOrder.getShippingCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(capturedOrder.getTotal()).isEqualByComparingTo(new BigDecimal("599.80")); // 2×299.90
        // IGV desglosado: 599.80/1.18 = 508.31 base → igv = 91.49
        assertThat(capturedOrder.getIgv()).isEqualByComparingTo(new BigDecimal("91.49"));
        // Comprobante persistido desde el request
        assertThat(capturedOrder.getDocumentType()).isEqualTo(TipoDocumento.BOLETA);
        assertThat(capturedOrder.getCustomerName()).isEqualTo("Juan Cliente");
        assertThat(capturedOrder.getCustomerDoc()).isEqualTo("12345678");

        // Verify ItemOrden saved with price snapshot
        ArgumentCaptor<ItemOrden> itemCaptor = ArgumentCaptor.forClass(ItemOrden.class);
        verify(orderItemRepository).save(itemCaptor.capture());
        ItemOrden capturedItem = itemCaptor.getValue();
        assertThat(capturedItem.getUnitPrice()).isEqualByComparingTo(new BigDecimal("299.90"));
        assertThat(capturedItem.getQuantity()).isEqualTo(2);

        // Verify stock decremented
        assertThat(p.getStock()).isEqualTo(3); // 5 - 2
        verify(productRepository).save(p);

        // Verify MovimientoStock saved with SALIDA type
        ArgumentCaptor<MovimientoStock> movCaptor = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(stockMovementRepository).save(movCaptor.capture());
        MovimientoStock capturedMov = movCaptor.getValue();
        assertThat(capturedMov.getType()).isEqualTo(TipoMovimiento.SALIDA);
        assertThat(capturedMov.getReason()).isEqualTo("Venta orden #1");
        assertThat(capturedMov.getReference()).isEqualTo("ORDER-1");
        assertThat(capturedMov.getCreatedBy()).isNull();
        assertThat(capturedMov.getQuantity()).isEqualTo(2);

        // Verify cart cleared
        verify(cartService).vaciarCarrito(email);
    }

    @Test
    void checkout_empty_cart_throws_EmptyCartException_and_no_order_saved() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Carrito c = cart(1L, u);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of()); // empty cart

        assertThatThrownBy(() -> service.confirmarCompra(email, boletaRequest()))
                .isInstanceOf(EmptyCartException.class);

        verify(orderRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(cartService, never()).vaciarCarrito(any());
    }

    @Test
    void checkout_no_cart_throws_EmptyCartException() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.empty()); // no cart at all

        assertThatThrownBy(() -> service.confirmarCompra(email, boletaRequest()))
                .isInstanceOf(EmptyCartException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkout_insufficient_stock_throws_and_no_order_saved() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Carrito c = cart(1L, u);
        Producto p = product(10L, "Notebook", new BigDecimal("299.90"), 1); // only 1 in stock
        ItemCarrito ci = cartItem(1L, c, p, 5); // requesting 5

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of(ci));
        when(productRepository.findByIdWithLock(10L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.confirmarCompra(email, boletaRequest()))
                .isInstanceOf(InsufficientStockException.class);

        // No order, no stock movement persisted
        verify(orderRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(cartService, never()).vaciarCarrito(any());
    }

    @Test
    void checkout_product_not_found_throws_ResourceNotFoundException() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Carrito c = cart(1L, u);
        Producto p = product(99L, "Ghost", BigDecimal.TEN, 5);
        ItemCarrito ci = cartItem(1L, c, p, 1);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of(ci));
        when(productRepository.findByIdWithLock(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmarCompra(email, boletaRequest()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkout_charges_20_shipping_when_subtotal_below_300() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Carrito c = cart(1L, u);
        Producto p = product(10L, "Mouse", new BigDecimal("100.00"), 5);
        ItemCarrito ci = cartItem(1L, c, p, 1); // subtotal = 100.00 < 300

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of(ci));
        when(productRepository.findByIdWithLock(10L)).thenReturn(Optional.of(p));
        when(orderRepository.save(any(Orden.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.save(any(ItemOrden.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(MovimientoStock.class))).thenReturn(new MovimientoStock());
        when(orderMapper.toResponse(any(), any())).thenReturn(sampleResponse());

        service.confirmarCompra(email, boletaRequest());

        ArgumentCaptor<Orden> captor = ArgumentCaptor.forClass(Orden.class);
        verify(orderRepository).save(captor.capture());
        Orden o = captor.getValue();
        assertThat(o.getSubtotal()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(o.getShippingCost()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(o.getTotal()).isEqualByComparingTo(new BigDecimal("120.00")); // 100 + 20
        // IGV: 120/1.18 = 101.69 base → igv = 18.31
        assertThat(o.getIgv()).isEqualByComparingTo(new BigDecimal("18.31"));
    }

    @Test
    void checkout_free_shipping_when_subtotal_reaches_300() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Carrito c = cart(1L, u);
        Producto p = product(10L, "Teclado", new BigDecimal("150.00"), 5);
        ItemCarrito ci = cartItem(1L, c, p, 2); // subtotal = 300.00 (umbral exacto)

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of(ci));
        when(productRepository.findByIdWithLock(10L)).thenReturn(Optional.of(p));
        when(orderRepository.save(any(Orden.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.save(any(ItemOrden.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(MovimientoStock.class))).thenReturn(new MovimientoStock());
        when(orderMapper.toResponse(any(), any())).thenReturn(sampleResponse());

        service.confirmarCompra(email, boletaRequest());

        ArgumentCaptor<Orden> captor = ArgumentCaptor.forClass(Orden.class);
        verify(orderRepository).save(captor.capture());
        Orden o = captor.getValue();
        assertThat(o.getShippingCost()).isEqualByComparingTo(BigDecimal.ZERO); // ≥ 300 → gratis
        assertThat(o.getTotal()).isEqualByComparingTo(new BigDecimal("300.00"));
        // IGV: 300/1.18 = 254.24 base → igv = 45.76
        assertThat(o.getIgv()).isEqualByComparingTo(new BigDecimal("45.76"));
    }

    @Test
    void checkout_rejects_factura_without_ruc_length() {
        // FACTURA con documento de 8 díg (DNI) → rechazo antes de tocar nada
        CheckoutRequest req = new CheckoutRequest(TipoDocumento.FACTURA, "ACME SAC", "12345678");

        assertThatThrownBy(() -> service.confirmarCompra("client@krypton.pe", req))
                .isInstanceOf(InvalidDocumentException.class);

        verify(userRepository, never()).findByEmail(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkout_rejects_boleta_with_ruc_length() {
        // BOLETA con documento de 11 díg (RUC) → rechazo
        CheckoutRequest req = new CheckoutRequest(TipoDocumento.BOLETA, "Juan", "20512345678");

        assertThatThrownBy(() -> service.confirmarCompra("client@krypton.pe", req))
                .isInstanceOf(InvalidDocumentException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkout_accepts_factura_with_valid_ruc() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Carrito c = cart(1L, u);
        Producto p = product(10L, "Notebook", new BigDecimal("299.90"), 5);
        ItemCarrito ci = cartItem(1L, c, p, 1);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(cartRepository.findByUser(u)).thenReturn(Optional.of(c));
        when(cartItemRepository.findByCart(c)).thenReturn(List.of(ci));
        when(productRepository.findByIdWithLock(10L)).thenReturn(Optional.of(p));
        when(orderRepository.save(any(Orden.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.save(any(ItemOrden.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stockMovementRepository.save(any(MovimientoStock.class))).thenReturn(new MovimientoStock());
        when(orderMapper.toResponse(any(), any())).thenReturn(sampleResponse());

        service.confirmarCompra(email, new CheckoutRequest(TipoDocumento.FACTURA, "ACME SAC", "20512345678"));

        ArgumentCaptor<Orden> captor = ArgumentCaptor.forClass(Orden.class);
        verify(orderRepository).save(captor.capture());
        Orden o = captor.getValue();
        assertThat(o.getDocumentType()).isEqualTo(TipoDocumento.FACTURA);
        assertThat(o.getCustomerDoc()).isEqualTo("20512345678");
    }

    // ─── READ GROUP ──────────────────────────────────────────────────────────────

    @Test
    void getMyOrders_returns_only_authenticated_user_orders_ordered_by_date_desc() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);

        Orden o1 = order(1L, u, BigDecimal.TEN, EstadoOrden.PENDIENTE);
        Orden o2 = order(2L, u, BigDecimal.TEN, EstadoOrden.CONFIRMADA);
        List<Orden> orders = List.of(o2, o1); // newest first

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(orderRepository.findByUserOrderByOrderDateDesc(u)).thenReturn(orders);
        when(orderItemRepository.findByOrder(any())).thenReturn(List.of());
        when(orderMapper.toResponse(any(), any())).thenAnswer(inv -> {
            Orden o = inv.getArgument(0);
            return new OrdenResponse(o.getId(), 3L, o.getOrderDate(), o.getStatus().name(),
                    "BOLETA", "Cliente", "00000000",
                    o.getTotal(), BigDecimal.ZERO, BigDecimal.ZERO, o.getTotal(), List.of());
        });

        List<OrdenResponse> result = service.misOrdenes(email);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(2L); // newest first
        assertThat(result.get(1).id()).isEqualTo(1L);
    }

    @Test
    void getMyOrder_returns_order_when_owner_matches() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Orden o = order(5L, u, BigDecimal.TEN, EstadoOrden.PENDIENTE);
        List<ItemOrden> items = List.of();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(orderRepository.findByIdAndUser(5L, u)).thenReturn(Optional.of(o));
        when(orderItemRepository.findByOrder(o)).thenReturn(items);
        when(orderMapper.toResponse(o, items)).thenReturn(sampleResponse());

        OrdenResponse result = service.miOrden(email, 5L);

        assertThat(result).isNotNull();
    }

    @Test
    void getMyOrder_IDOR_throws_ResourceNotFoundException() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(orderRepository.findByIdAndUser(9L, u)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.miOrden(email, 9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── PAY GROUP ───────────────────────────────────────────────────────────────

    @Test
    void pay_happy_path_transitions_pendiente_to_confirmada() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Orden o = order(3L, u, BigDecimal.TEN, EstadoOrden.PENDIENTE);
        List<ItemOrden> items = List.of();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(orderRepository.findByIdAndUser(3L, u)).thenReturn(Optional.of(o));
        when(orderRepository.save(o)).thenReturn(o);
        when(orderItemRepository.findByOrder(o)).thenReturn(items);
        when(orderMapper.toResponse(o, items)).thenReturn(
                new OrdenResponse(3L, 3L, Instant.now(), "CONFIRMADA",
                        "BOLETA", "Cliente", "00000000",
                        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, List.of()));

        OrdenResponse result = service.pagar(email, 3L, new PaymentRequest(MetodoPago.YAPE));

        assertThat(result.status()).isEqualTo("CONFIRMADA");
        assertThat(o.getStatus()).isEqualTo(EstadoOrden.CONFIRMADA);
        assertThat(o.getPaymentMethod()).isEqualTo(MetodoPago.YAPE); // el método de pago queda persistido
        verify(orderRepository).save(o);
    }

    @Test
    void pay_already_confirmada_throws_OrderStatusTransitionException() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Orden o = order(4L, u, BigDecimal.TEN, EstadoOrden.CONFIRMADA);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(orderRepository.findByIdAndUser(4L, u)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.pagar(email, 4L, new PaymentRequest(MetodoPago.YAPE)))
                .isInstanceOf(OrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void pay_cancelada_throws_OrderStatusTransitionException() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Orden o = order(7L, u, BigDecimal.TEN, EstadoOrden.CANCELADA);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(orderRepository.findByIdAndUser(7L, u)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.pagar(email, 7L, new PaymentRequest(MetodoPago.DEBIT_CARD)))
                .isInstanceOf(OrderStatusTransitionException.class);
    }

    @Test
    void pay_IDOR_throws_ResourceNotFoundException() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(orderRepository.findByIdAndUser(8L, u)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pagar(email, 8L, new PaymentRequest(MetodoPago.CREDIT_CARD)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── comprobante PDF ──────────────────────────────────────────────────────────

    @Test
    void getMyComprobantePdf_paid_order_returns_pdf_bytes() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Orden o = order(5L, u, BigDecimal.TEN, EstadoOrden.CONFIRMADA);
        byte[] pdf = new byte[]{ 0x25, 0x50, 0x44, 0x46 };

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(orderRepository.findByIdAndUser(5L, u)).thenReturn(Optional.of(o));
        when(orderItemRepository.findByOrder(o)).thenReturn(List.of());
        when(comprobanteExporter.export(eq(o), any())).thenReturn(pdf);

        byte[] result = service.miComprobantePdf(email, 5L);

        assertThat(result).isEqualTo(pdf);
    }

    @Test
    void getMyComprobantePdf_pending_order_throws_ComprobanteNotAvailable() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);
        Orden o = order(6L, u, BigDecimal.TEN, EstadoOrden.PENDIENTE);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(orderRepository.findByIdAndUser(6L, u)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.miComprobantePdf(email, 6L))
                .isInstanceOf(ComprobanteNotAvailableException.class);
        verify(comprobanteExporter, never()).export(any(), any());
    }

    @Test
    void getMyComprobantePdf_IDOR_throws_ResourceNotFound() {
        String email = "client@krypton.pe";
        Usuario u = user(3L, email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        when(orderRepository.findByIdAndUser(9L, u)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.miComprobantePdf(email, 9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getComprobantePdf_admin_paid_order_returns_pdf_bytes() {
        Orden o = order(7L, user(3L, "x@krypton.pe"), BigDecimal.TEN, EstadoOrden.ENTREGADO);
        byte[] pdf = new byte[]{ 0x25, 0x50, 0x44, 0x46 };

        when(orderRepository.findById(7L)).thenReturn(Optional.of(o));
        when(orderItemRepository.findByOrder(o)).thenReturn(List.of());
        when(comprobanteExporter.export(eq(o), any())).thenReturn(pdf);

        byte[] result = service.comprobantePdf(7L);

        assertThat(result).isEqualTo(pdf);
    }

    // ─── ADMIN GROUP ─────────────────────────────────────────────────────────────

    @Test
    void getAllOrders_returns_page_of_all_orders() {
        Pageable pageable = PageRequest.of(0, 10);
        Usuario u = user(3L, "client@krypton.pe");
        Orden o = order(1L, u, BigDecimal.TEN, EstadoOrden.PENDIENTE);
        Page<Orden> page = new PageImpl<>(List.of(o), pageable, 1);

        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(orderItemRepository.findByOrder(o)).thenReturn(List.of());
        when(orderMapper.toResponse(eq(o), any())).thenReturn(sampleResponse());

        PageResponse<OrdenResponse> result = service.listarOrdenes(null, null, null, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getOrder_returns_any_order_by_id() {
        Usuario u = user(3L, "client@krypton.pe");
        Orden o = order(10L, u, BigDecimal.TEN, EstadoOrden.PENDIENTE);
        List<ItemOrden> items = List.of();

        when(orderRepository.findById(10L)).thenReturn(Optional.of(o));
        when(orderItemRepository.findByOrder(o)).thenReturn(items);
        when(orderMapper.toResponse(o, items)).thenReturn(sampleResponse());

        OrdenResponse result = service.obtenerOrden(10L);

        assertThat(result).isNotNull();
    }

    @Test
    void getOrder_not_found_throws_ResourceNotFoundException() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerOrden(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_confirmada_to_cancelada_reverts_stock_with_entrada_movement() {
        Usuario u = user(3L, "client@krypton.pe");
        Orden o = order(2L, u, new BigDecimal("599.80"), EstadoOrden.CONFIRMADA);
        Producto p = product(10L, "Notebook", new BigDecimal("299.90"), 3); // stock tras la venta
        ItemOrden item = orderItem(1L, o, p, 2, new BigDecimal("299.90"));
        List<ItemOrden> items = List.of(item);

        when(orderRepository.findById(2L)).thenReturn(Optional.of(o));
        when(orderItemRepository.findByOrder(o)).thenReturn(items);
        when(productRepository.findByIdWithLock(10L)).thenReturn(Optional.of(p));
        when(orderRepository.save(o)).thenReturn(o);
        when(orderMapper.toResponse(eq(o), any())).thenReturn(
                new OrdenResponse(2L, 3L, Instant.now(), "CANCELADA",
                        "BOLETA", "Cliente", "00000000",
                        new BigDecimal("599.80"), BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("599.80"), List.of()));

        OrdenResponse result = service.actualizarEstado(2L, EstadoOrden.CANCELADA);

        assertThat(o.getStatus()).isEqualTo(EstadoOrden.CANCELADA);
        assertThat(p.getStock()).isEqualTo(5); // 3 + 2 repuestas
        verify(productRepository).save(p);

        ArgumentCaptor<MovimientoStock> movCaptor = ArgumentCaptor.forClass(MovimientoStock.class);
        verify(stockMovementRepository).save(movCaptor.capture());
        MovimientoStock mov = movCaptor.getValue();
        assertThat(mov.getType()).isEqualTo(TipoMovimiento.ENTRADA);
        assertThat(mov.getQuantity()).isEqualTo(2);
        assertThat(mov.getReason()).isEqualTo("Cancelación orden #2");
        assertThat(mov.getReference()).isEqualTo("ORDER-2");
        assertThat(mov.getCreatedBy()).isNull();
        assertThat(result.status()).isEqualTo("CANCELADA");
    }

    @Test
    void updateStatus_pendiente_to_cancelada_also_reverts_stock() {
        // Clave: el stock se descontó en checkout (PENDIENTE), así que cancelar
        // sin haber pagado TAMBIÉN debe reponerlo.
        Usuario u = user(3L, "client@krypton.pe");
        Orden o = order(2L, u, new BigDecimal("299.90"), EstadoOrden.PENDIENTE);
        Producto p = product(10L, "Notebook", new BigDecimal("299.90"), 4);
        ItemOrden item = orderItem(1L, o, p, 1, new BigDecimal("299.90"));

        when(orderRepository.findById(2L)).thenReturn(Optional.of(o));
        when(orderItemRepository.findByOrder(o)).thenReturn(List.of(item));
        when(productRepository.findByIdWithLock(10L)).thenReturn(Optional.of(p));
        when(orderRepository.save(o)).thenReturn(o);
        when(orderMapper.toResponse(eq(o), any())).thenReturn(
                new OrdenResponse(2L, 3L, Instant.now(), "CANCELADA",
                        "BOLETA", "Cliente", "00000000",
                        new BigDecimal("299.90"), BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("299.90"), List.of()));

        service.actualizarEstado(2L, EstadoOrden.CANCELADA);

        assertThat(o.getStatus()).isEqualTo(EstadoOrden.CANCELADA);
        assertThat(p.getStock()).isEqualTo(5); // 4 + 1
        verify(stockMovementRepository).save(any(MovimientoStock.class));
    }

    @Test
    void updateStatus_cancelada_to_confirmada_throws_and_nothing_persisted() {
        // CANCELADA es terminal: revivir una orden cancelada es ilegal.
        Usuario u = user(3L, "client@krypton.pe");
        Orden o = order(2L, u, BigDecimal.TEN, EstadoOrden.CANCELADA);

        when(orderRepository.findById(2L)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.actualizarEstado(2L, EstadoOrden.CONFIRMADA))
                .isInstanceOf(OrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateStatus_pendiente_to_confirmada_does_not_touch_stock() {
        // Transición legal que NO es cancelación → no reposición de stock.
        Usuario u = user(3L, "client@krypton.pe");
        Orden o = order(2L, u, BigDecimal.TEN, EstadoOrden.PENDIENTE);

        when(orderRepository.findById(2L)).thenReturn(Optional.of(o));
        when(orderRepository.save(o)).thenReturn(o);
        when(orderItemRepository.findByOrder(o)).thenReturn(List.of());
        when(orderMapper.toResponse(eq(o), any())).thenReturn(
                new OrdenResponse(2L, 3L, Instant.now(), "CONFIRMADA",
                        "BOLETA", "Cliente", "00000000",
                        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, List.of()));

        OrdenResponse result = service.actualizarEstado(2L, EstadoOrden.CONFIRMADA);

        assertThat(o.getStatus()).isEqualTo(EstadoOrden.CONFIRMADA);
        verify(stockMovementRepository, never()).save(any());
        verify(productRepository, never()).save(any());
        assertThat(result.status()).isEqualTo("CONFIRMADA");
    }
}
