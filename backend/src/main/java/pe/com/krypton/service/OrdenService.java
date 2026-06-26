package pe.com.krypton.service;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import pe.com.krypton.dto.request.CheckoutRequest;
import pe.com.krypton.dto.request.PaymentRequest;
import pe.com.krypton.dto.response.OrderResponse;
import pe.com.krypton.dto.response.PageResponse;
import pe.com.krypton.entity.enums.EstadoOrden;

public interface OrdenService {

    /**
     * Atomic checkout: cart → Orden (PENDIENTE), stock decrement, MovimientoStock, clear cart.
     * Calcula el envío (gratis ≥ S/300, si no S/20) y desglosa el IGV (el precio ya lo
     * incluye). El comprobante (boleta/factura + receptor) viene en {@code request}.
     */
    OrderResponse checkout(String email, CheckoutRequest request);

    /** Returns the authenticated client's orders ordered by date DESC. */
    List<OrderResponse> getMyOrders(String email);

    /** Returns the client's own order detail. Throws ResourceNotFoundException (404) if IDOR. */
    OrderResponse getMyOrder(String email, Long orderId);

    /**
     * Simulated payment: PENDIENTE → CONFIRMADA.
     * Throws ResourceNotFoundException (404) if IDOR.
     * Throws OrderStatusTransitionException (422) if not PENDIENTE.
     */
    OrderResponse pay(String email, Long orderId, PaymentRequest request);

    /** Admin: lista paginada de órdenes con filtros opcionales (estado, rango de fecha). */
    PageResponse<OrderResponse> getAllOrders(EstadoOrden status, Instant from, Instant to, Pageable pageable);

    /** Admin: single order by id. Throws ResourceNotFoundException (404) if not found. */
    OrderResponse getOrder(Long orderId);

    /**
     * Admin: cambia el estado de una orden respetando la máquina de estados
     * (EstadoOrdenPolicy). Transición ilegal → OrderStatusTransitionException (422).
     * Cancelar (→ CANCELADA) repone el stock con un MovimientoStock(ENTRADA).
     */
    OrderResponse updateStatus(Long orderId, EstadoOrden newStatus);

    /**
     * Cliente: PDF del comprobante (boleta/factura) de su propio pedido PAGADO.
     * ResourceNotFoundException (404) si IDOR; ComprobanteNotAvailableException (409)
     * si el pedido no está pagado (PENDIENTE/CANCELADA).
     */
    byte[] getMyComprobantePdf(String email, Long orderId);

    /**
     * Admin: PDF del comprobante de cualquier pedido PAGADO.
     * ResourceNotFoundException (404) si no existe; ComprobanteNotAvailableException (409)
     * si el pedido no está pagado.
     */
    byte[] getComprobantePdf(Long orderId);
}
