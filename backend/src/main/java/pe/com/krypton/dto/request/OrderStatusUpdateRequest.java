package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotNull;
import pe.com.krypton.model.enums.OrderStatus;

public record OrderStatusUpdateRequest(@NotNull OrderStatus status) {
}
