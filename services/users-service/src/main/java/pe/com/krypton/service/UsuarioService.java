package pe.com.krypton.service;

import java.util.List;
import pe.com.krypton.dto.request.CreateUsuarioRequest;
import pe.com.krypton.dto.response.UsuarioResponse;
import pe.com.krypton.entity.enums.Rol;

/** Gestión de usuarios por un ADMIN. */
public interface UsuarioService {

    List<UsuarioResponse> listar();

    /** Crea un usuario con rol elegible (CLIENTE o ADMIN). */
    UsuarioResponse registrar(CreateUsuarioRequest request);

    /** Cambia el rol; bloquea si degrada al último ADMIN activo. */
    UsuarioResponse cambiarRol(Long id, Rol newRole);

    /** Activa/desactiva (baja lógica); bloquea si desactiva al último ADMIN activo. */
    UsuarioResponse cambiarEstado(Long id, boolean active);
}
