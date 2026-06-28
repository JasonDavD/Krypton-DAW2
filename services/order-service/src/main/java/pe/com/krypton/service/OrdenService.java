package pe.com.krypton.service;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import pe.com.krypton.dto.request.CheckoutRequest;
import pe.com.krypton.dto.request.PaymentRequest;
import pe.com.krypton.dto.response.OrdenResponse;
import pe.com.krypton.dto.response.PageResponse;
import pe.com.krypton.entity.enums.EstadoOrden;

/** Operaciones de pedidos del usuario. */
public interface OrdenService {

    /**
     * Confirma la compra (checkout). La saga distribuida (descuento de stock en catalog +
     * creación de orden + compensación) la implementa el orquestador.
     */
    OrdenResponse confirmarCompra(String email, CheckoutRequest request);

    /**
     * Paga una orden PENDIENTE: cobra vía payment-service (Feign) y, si se aprueba, transiciona
     * la orden a CONFIRMADA. 422 si la orden no admite la transición; 402 si el pago se rechaza.
     */
    OrdenResponse pagar(String email, Long orderId, PaymentRequest request);

    /** Órdenes del usuario, más recientes primero. */
    List<OrdenResponse> misOrdenes(String email);

    /** Una orden propia por id (404 si no existe o no es del usuario). */
    OrdenResponse miOrden(String email, Long id);

    /**
     * PDF del comprobante (boleta/factura) de una orden propia PAGADA.
     * 404 si no existe o no es del usuario; 409 si la orden no está pagada.
     */
    byte[] miComprobantePdf(String email, Long id);

    // ── Admin (gestión de TODAS las órdenes) ──

    /** Listado paginado de todas las órdenes, con filtros opcionales (status, rango de fecha). */
    PageResponse<OrdenResponse> listarOrdenes(EstadoOrden status, Instant from, Instant to, Pageable pageable);

    /** Cualquier orden por id (404 si no existe). */
    OrdenResponse obtenerOrden(Long id);

    /** Cambia el estado validando la máquina de estados (422 si es ilegal). Cancelar repone stock. */
    OrdenResponse actualizarEstado(Long id, EstadoOrden newStatus);

    /** PDF del comprobante de cualquier orden pagada (404 si no existe; 409 si no está pagada). */
    byte[] comprobantePdf(Long id);
}
