package pe.com.krypton.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Un ítem a mover en el kardex: qué producto y qué cantidad. */
public record StockItemRequest(
        @NotNull Long productId,
        @Min(1) int quantity) {
}
