package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotNull;
import pe.com.krypton.entity.enums.MetodoPago;

/** Datos del pago: con qué método se paga la orden. */
public record PaymentRequest(@NotNull MetodoPago method) {
}
