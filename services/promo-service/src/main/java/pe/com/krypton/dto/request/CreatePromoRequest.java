package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import pe.com.krypton.entity.enums.DescuentoTipo;

/** Alta de un cupon (solo ADMIN). value se interpreta segun type. */
public record CreatePromoRequest(
        @NotBlank String code,
        @NotNull DescuentoTipo type,
        @NotNull @Positive BigDecimal value) {
}
