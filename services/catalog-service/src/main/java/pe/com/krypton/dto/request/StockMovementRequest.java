package pe.com.krypton.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Pedido de movimiento de stock que envía order-service vía Feign.
 * <ul>
 *   <li>{@code reference}: origen del movimiento (ej. "ORDER-123") → queda en el kardex.</li>
 *   <li>{@code createdBy}: id del usuario que disparó la compra (nullable; vive en otro servicio).</li>
 *   <li>{@code items}: qué productos y cuánto.</li>
 * </ul>
 */
public record StockMovementRequest(
        String reference,
        Long createdBy,
        @NotEmpty @Valid List<StockItemRequest> items) {
}
