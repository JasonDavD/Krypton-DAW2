package pe.com.krypton.mapper;

import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.UserResponse;
import pe.com.krypton.entity.Usuario;

/** Traduce la entidad Usuario a su DTO de salida (sin exponer el password). */
@Component
public class UsuarioMapper {

    public UserResponse toResponse(Usuario user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
