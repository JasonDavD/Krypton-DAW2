package pe.com.krypton.client.dto;

/** Item del contrato de stock con catalog-service (espejo del DTO de catalog). */
public record StockItemRequest(Long productId, int quantity) {
}
