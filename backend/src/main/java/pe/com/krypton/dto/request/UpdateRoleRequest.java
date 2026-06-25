package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotNull;
import pe.com.krypton.entity.enums.Role;

public record UpdateRoleRequest(@NotNull Role role) {
}
