package pe.com.krypton.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta de una resena: producto + estrellas (1..5) + comentario opcional (max 1000). */
public record CreateResenaRequest(
        @NotNull Long productId,
        @Min(1) @Max(5) int rating,
        @Size(max = 1000) String comment) {
}
