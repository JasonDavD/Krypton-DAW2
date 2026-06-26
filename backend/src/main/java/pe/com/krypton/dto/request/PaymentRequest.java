package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotNull;
import pe.com.krypton.entity.enums.MetodoPago;

public record PaymentRequest(@NotNull MetodoPago method) {
}
