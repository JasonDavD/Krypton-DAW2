package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotNull;
import pe.com.krypton.entity.enums.EstadoOrden;

/** Cuerpo del cambio de estado admin de una orden. */
public record OrderStatusUpdateRequest(@NotNull EstadoOrden status) {
}
