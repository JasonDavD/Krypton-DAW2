package pe.com.krypton.dto.response;

import java.time.Instant;
import pe.com.krypton.model.enums.Role;

/** Vista pública de un usuario. NUNCA incluye el password. */
public record UserResponse(
        Long id, String name, String email, Role role, boolean active, Instant createdAt) {
}
