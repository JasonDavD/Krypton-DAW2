package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotNull;
import pe.com.krypton.entity.enums.EstadoOrden;

public record OrderStatusUpdateRequest(@NotNull EstadoOrden status) {
}
