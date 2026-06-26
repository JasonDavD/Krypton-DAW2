package pe.com.krypton.client.dto;

import java.util.List;

/**
 * Pedido de movimiento de stock a catalog-service (espejo de su DTO).
 * reference: "ORDER-{id}"; createdBy: id del usuario (null — order solo tiene el email).
 */
public record StockMovementRequest(String reference, Long createdBy, List<StockItemRequest> items) {
}
