package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Solicitud de cobro que envia order-service en el checkout. */
public record ChargeRequest(
        @NotNull Long orderId,
        @NotNull BigDecimal amount,
        @NotBlank String method) {
}
