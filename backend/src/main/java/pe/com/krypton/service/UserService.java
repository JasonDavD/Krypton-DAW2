package pe.com.krypton.service;

import java.util.List;
import pe.com.krypton.dto.request.CreateUserRequest;
import pe.com.krypton.dto.response.UserResponse;
import pe.com.krypton.model.enums.Role;

/** Gestión de usuarios por un ADMIN. */
public interface UserService {

    List<UserResponse> listAll();

    /** Crea un usuario con rol elegible (CLIENTE o ADMIN). */
    UserResponse create(CreateUserRequest request);

    /** Cambia el rol; bloquea si degrada al último ADMIN activo. */
    UserResponse changeRole(Long id, Role newRole);

    /** Activa/desactiva (baja lógica); bloquea si desactiva al último ADMIN activo. */
    UserResponse setStatus(Long id, boolean active);
}
