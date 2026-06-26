package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Solicitud para aplicar un cupon sobre un monto (amount). */
public record AplicarPromoRequest(
        @NotBlank String code,
        @NotNull @Positive BigDecimal amount) {
}
