package pe.com.krypton.dto.request;

import jakarta.validation.constraints.NotNull;
import pe.com.krypton.entity.enums.Rol;

public record UpdateRoleRequest(@NotNull Rol role) {
}
