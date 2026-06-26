package pe.com.krypton.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Cambio de cantidad de una línea del carrito. */
public record UpdateQuantityRequest(
        @NotNull @Min(1) Integer quantity) {
}
