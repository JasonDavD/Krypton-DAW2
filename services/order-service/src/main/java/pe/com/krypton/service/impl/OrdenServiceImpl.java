package pe.com.krypton.service.impl;

import feign.FeignException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.client.CatalogClient;
import pe.com.krypton.client.PaymentClient;
import pe.com.krypton.client.PromoClient;
import pe.com.krypton.client.dto.ApplyPromoRequest;
import pe.com.krypton.client.dto.ChargeRequest;
import pe.com.krypton.client.dto.DiscountResponse;
import pe.com.krypton.client.dto.PaymentResponse;
import pe.com.krypton.client.dto.ProductoResponse;
import pe.com.krypton.client.dto.StockItemRequest;
import pe.com.krypton.client.dto.StockMovementRequest;
import pe.com.krypton.dto.request.CheckoutRequest;
import pe.com.krypton.dto.request.PaymentRequest;
import pe.com.krypton.dto.response.OrdenResponse;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.ItemCarrito;
import pe.com.krypton.entity.ItemOrden;
import pe.com.krypton.entity.Orden;
import pe.com.krypton.entity.enums.EstadoOrden;
import pe.com.krypton.entity.enums.TipoDocumento;
import pe.com.krypton.exception.EmptyCartException;
import pe.com.krypton.exception.InsufficientStockException;
import pe.com.krypton.exception.InvalidCouponException;
import pe.com.krypton.exception.InvalidDocumentException;
import pe.com.krypton.exception.PaymentDeclinedException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.OrdenMapper;
import pe.com.krypton.policy.EstadoOrdenPolicy;
import pe.com.krypton.repository.CarritoRepository;
import pe.com.krypton.repository.ItemCarritoRepository;
import pe.com.krypton.repository.ItemOrdenRepository;
import pe.com.krypton.repository.OrdenRepository;
import pe.com.krypton.service.OrdenService;

/**
 * Pedidos del usuario. Las lecturas (misOrdenes/miOrden) son históricas: NO llaman a catalog
 * (la orden guarda el snapshot de nombre y precio).
 *
 * <p>{@link #confirmarCompra} es el CHECKOUT DISTRIBUIDO (F5): una SAGA con compensación.
 * Como ninguna transacción cruza order ↔ catalog, el "rollback distribuido" se hace a mano:
 * si el descuento de stock en catalog ya commiteó y después algo falla localmente, se llama a
 * {@code restoreStock} para deshacerlo.
 */
@Service
public class OrdenServiceImpl implements OrdenService {

    // Reglas de negocio (idénticas al monolito).
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("300.00");
    private static final BigDecimal SHIPPING_COST = new BigDecimal("20.00");
    private static final BigDecimal IGV_DIVISOR = new BigDecimal("1.18"); // el precio YA incluye IGV

    private final OrdenRepository ordenRepository;
    private final ItemOrdenRepository itemOrdenRepository;
    private final CarritoRepository carritoRepository;
    private final ItemCarritoRepository itemCarritoRepository;
    private final OrdenMapper ordenMapper;
    private final CatalogClient catalogClient;
    private final PaymentClient paymentClient;
    private final PromoClient promoClient;
    private final EstadoOrdenPolicy estadoOrdenPolicy;

    public OrdenServiceImpl(OrdenRepository ordenRepository,
                            ItemOrdenRepository itemOrdenRepository,
                            CarritoRepository carritoRepository,
                            ItemCarritoRepository itemCarritoRepository,
                            OrdenMapper ordenMapper,
                            CatalogClient catalogClient,
                            PaymentClient paymentClient,
                            PromoClient promoClient,
                            EstadoOrdenPolicy estadoOrdenPolicy) {
        this.ordenRepository = ordenRepository;
        this.itemOrdenRepository = itemOrdenRepository;
        this.carritoRepository = carritoRepository;
        this.itemCarritoRepository = itemCarritoRepository;
        this.ordenMapper = ordenMapper;
        this.catalogClient = catalogClient;
        this.paymentClient = paymentClient;
        this.promoClient = promoClient;
        this.estadoOrdenPolicy = estadoOrdenPolicy;
    }

    @Override
    @Transactional
    public OrdenResponse confirmarCompra(String email, CheckoutRequest request) {
        validarDocumento(request);

        Carrito cart = carritoRepository.findByUserEmail(email)
                .orElseThrow(() -> new EmptyCartException("El carrito está vacío"));
        List<ItemCarrito> cartItems = itemCarritoRepository.findByCart(cart);
        if (cartItems.isEmpty()) {
            throw new EmptyCartException("El carrito está vacío");
        }

        // 1) Por cada ítem: traer el producto de catalog (Feign) → acumular subtotal y armar el
        //    snapshot de la línea (nombre + precio CONGELADOS) y el pedido de descuento de stock.
        BigDecimal subtotal = BigDecimal.ZERO;
        List<ItemOrden> lineas = new ArrayList<>();
        List<StockItemRequest> stockItems = new ArrayList<>();
        for (ItemCarrito ci : cartItems) {
            ProductoResponse producto = catalogClient.getProduct(ci.getProductId());

            subtotal = subtotal.add(producto.price().multiply(BigDecimal.valueOf(ci.getQuantity())));

            ItemOrden linea = new ItemOrden();
            linea.setProductId(producto.id());
            linea.setProductName(producto.name());   // snapshot del nombre
            linea.setQuantity(ci.getQuantity());
            linea.setUnitPrice(producto.price());     // snapshot del precio
            lineas.add(linea);

            stockItems.add(new StockItemRequest(ci.getProductId(), ci.getQuantity()));
        }

        // 2) Cupón (Feign a promo-service; 0 si no hay), envío (gratis si subtotal ≥ S/300),
        //    total (= subtotal − descuento + envío) e IGV desglosado hacia adentro.
        BigDecimal discount = aplicarCupon(request.couponCode(), subtotal);
        BigDecimal shipping = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO : SHIPPING_COST;
        BigDecimal total = subtotal.subtract(discount).add(shipping);
        BigDecimal base = total.divide(IGV_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(base);

        // 3) Crear la orden (PENDIENTE) y guardarla para obtener su id (lo usamos como referencia).
        Orden orden = new Orden();
        orden.setUserEmail(email);
        orden.setOrderDate(Instant.now());
        orden.setStatus(EstadoOrden.PENDIENTE);
        orden.setDocumentType(request.documentType());
        orden.setCustomerName(request.customerName());
        orden.setCustomerDoc(request.customerDoc());
        orden.setSubtotal(subtotal);
        orden.setDiscount(discount);
        orden.setShippingCost(shipping);
        orden.setIgv(igv);
        orden.setTotal(total);
        ordenRepository.save(orden); // INSERT dentro de la TX local → asigna id

        String reference = "ORDER-" + orden.getId();
        StockMovementRequest stockRequest = new StockMovementRequest(reference, null, stockItems);

        // 4) DESCONTAR stock en catalog (Feign). catalog lo hace en SU propia transacción.
        try {
            catalogClient.decreaseStock(stockRequest);
        } catch (FeignException ex) {
            if (ex.status() == 422) {
                // catalog rechazó por falta de stock: NO commiteó nada → la TX local hace rollback
                // de la orden. No hay que compensar (no se descontó).
                throw new InsufficientStockException("Stock insuficiente para completar la compra");
            }
            throw ex; // 404 / 5xx → lo mapea el GlobalExceptionHandler (tampoco hay qué compensar)
        }

        // 5) El stock YA está descontado (committeado en catalog). Si algo falla de acá en adelante,
        //    hay que COMPENSAR (restaurar el stock), porque la TX local no puede deshacer lo de catalog.
        try {
            for (ItemOrden linea : lineas) {
                linea.setOrder(orden);
                itemOrdenRepository.save(linea);
            }
            itemCarritoRepository.deleteByCart(cart); // vaciar el carrito
            cart.setUpdatedAt(Instant.now());
            carritoRepository.save(cart);
            return ordenMapper.toResponse(orden, lineas);
        } catch (RuntimeException ex) {
            catalogClient.restoreStock(stockRequest); // ← COMPENSACIÓN: deshacer el descuento
            throw ex;                                 // la TX local hace rollback de orden + líneas
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdenResponse> misOrdenes(String email) {
        return ordenRepository.findByUserEmailOrderByOrderDateDesc(email).stream()
                .map(orden -> ordenMapper.toResponse(orden, itemOrdenRepository.findByOrder(orden)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrdenResponse miOrden(String email, Long id) {
        Orden orden = ordenRepository.findByIdAndUserEmail(id, email)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + id));
        return ordenMapper.toResponse(orden, itemOrdenRepository.findByOrder(orden));
    }

    @Override
    @Transactional
    public OrdenResponse pagar(String email, Long orderId, PaymentRequest request) {
        Orden orden = ordenRepository.findByIdAndUserEmail(orderId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + orderId));

        // 1) ¿la orden admite pasar a CONFIRMADA desde su estado actual? (máquina de estados)
        estadoOrdenPolicy.assertCanTransition(orden.getStatus(), EstadoOrden.CONFIRMADA);

        // 2) cobrar vía Feign a payment-service (otra llamada SÍNCRONA entre servicios)
        PaymentResponse pago = paymentClient.charge(
                new ChargeRequest(orden.getId(), orden.getTotal(), request.method().name()));
        if (!"APPROVED".equals(pago.status())) {
            throw new PaymentDeclinedException("El pago fue rechazado"); // 402; la TX revierte
        }

        // 3) pago aprobado → confirmar la orden y registrar el método de pago
        orden.setStatus(EstadoOrden.CONFIRMADA);
        orden.setPaymentMethod(request.method());
        ordenRepository.save(orden);
        return ordenMapper.toResponse(orden, itemOrdenRepository.findByOrder(orden));
    }

    /** Aplica el cupón vía promo-service (Feign). 0 si no hay; 422 (InvalidCoupon) si es inválido. */
    private BigDecimal aplicarCupon(String couponCode, BigDecimal subtotal) {
        if (couponCode == null || couponCode.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            DiscountResponse desc = promoClient.applyPromo(new ApplyPromoRequest(couponCode, subtotal));
            return desc.discount();
        } catch (FeignException ex) {
            if (ex.status() == 422 || ex.status() == 404) {
                throw new InvalidCouponException("Cupón inválido: " + couponCode);
            }
            throw ex; // 5xx u otro → lo mapea el GlobalExceptionHandler
        }
    }

    /** FACTURA exige RUC (11 dígitos); BOLETA exige DNI (8). 422 si no corresponde. */
    private void validarDocumento(CheckoutRequest request) {
        String doc = request.customerDoc();
        if (request.documentType() == TipoDocumento.FACTURA && (doc == null || doc.length() != 11)) {
            throw new InvalidDocumentException("La factura requiere RUC de 11 dígitos");
        }
        if (request.documentType() == TipoDocumento.BOLETA && (doc == null || doc.length() != 8)) {
            throw new InvalidDocumentException("La boleta requiere DNI de 8 dígitos");
        }
    }
}
