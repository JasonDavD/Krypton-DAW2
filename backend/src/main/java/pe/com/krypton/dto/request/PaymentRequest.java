package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotNull;
import pe.com.krypton.entity.enums.PaymentMethod;

public record PaymentRequest(@NotNull PaymentMethod method) {
}
