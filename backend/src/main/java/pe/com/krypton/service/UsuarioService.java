package pe.com.krypton.service;

import java.util.List;
import pe.com.krypton.dto.request.CreateUsuarioRequest;
import pe.com.krypton.dto.response.UsuarioResponse;
import pe.com.krypton.entity.enums.Rol;

/** Gestión de usuarios por un ADMIN. */
public interface UsuarioService {

    List<UsuarioResponse> listAll();

    /** Crea un usuario con rol elegible (CLIENTE o ADMIN). */
    UsuarioResponse create(CreateUsuarioRequest request);

    /** Cambia el rol; bloquea si degrada al último ADMIN activo. */
    UsuarioResponse changeRole(Long id, Rol newRole);

    /** Activa/desactiva (baja lógica); bloquea si desactiva al último ADMIN activo. */
    UsuarioResponse setStatus(Long id, boolean active);
}
