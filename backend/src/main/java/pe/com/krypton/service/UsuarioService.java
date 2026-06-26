package pe.com.krypton.service;

import java.util.List;
import pe.com.krypton.dto.request.CreateUserRequest;
import pe.com.krypton.dto.response.UserResponse;
import pe.com.krypton.entity.enums.Rol;

/** Gestión de usuarios por un ADMIN. */
public interface UsuarioService {

    List<UserResponse> listAll();

    /** Crea un usuario con rol elegible (CLIENTE o ADMIN). */
    UserResponse create(CreateUserRequest request);

    /** Cambia el rol; bloquea si degrada al último ADMIN activo. */
    UserResponse changeRole(Long id, Rol newRole);

    /** Activa/desactiva (baja lógica); bloquea si desactiva al último ADMIN activo. */
    UserResponse setStatus(Long id, boolean active);
}
