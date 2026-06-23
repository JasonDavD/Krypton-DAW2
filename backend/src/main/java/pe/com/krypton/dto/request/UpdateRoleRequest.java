package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotNull;
import pe.com.krypton.model.enums.Role;

public record UpdateRoleRequest(@NotNull Role role) {
}
