package pe.com.krypton.dto.response;

import java.time.Instant;
import pe.com.krypton.entity.enums.Rol;

/** Vista pública de un usuario. NUNCA incluye el password. */
public record UserResponse(
        Long id, String name, String email, Rol role, boolean active, Instant createdAt) {
}
