package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotNull;
import pe.com.krypton.model.enums.PaymentMethod;

public record PaymentRequest(@NotNull PaymentMethod method) {
}
