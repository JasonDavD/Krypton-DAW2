package pe.com.krypton.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Alta de una línea al carrito. */
public record ItemCarritoRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity) {
}
