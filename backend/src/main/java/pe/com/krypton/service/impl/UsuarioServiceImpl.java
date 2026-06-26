package pe.com.krypton.service.impl;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.request.CreateUsuarioRequest;
import pe.com.krypton.dto.response.UsuarioResponse;
import pe.com.krypton.exception.DuplicateEmailException;
import pe.com.krypton.exception.LastAdminException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.UsuarioMapper;
import pe.com.krypton.entity.Usuario;
import pe.com.krypton.entity.enums.Rol;
import pe.com.krypton.repository.UsuarioRepository;
import pe.com.krypton.service.UsuarioService;

@Service
public class UsuarioServiceImpl extends ICRUDImpl<Usuario, Long> implements UsuarioService {

    private final UsuarioRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper userMapper;

    public UsuarioServiceImpl(UsuarioRepository userRepository, PasswordEncoder passwordEncoder, UsuarioMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /** Repository que usa el CRUD genérico heredado (guardar/listarTodos/...). */
    @Override
    protected JpaRepository<Usuario, Long> repo() {
        return userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return listarTodos().stream().map(userMapper::toResponse).toList();   // ← heredado de ICRUDImpl
    }

    @Override
    @Transactional
    public UsuarioResponse registrar(CreateUsuarioRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("El email ya está registrado");
        }
        Usuario user = new Usuario();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role()); // rol elegible: el ADMIN puede crear otro ADMIN
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        return userMapper.toResponse(guardar(user));   // ← heredado de ICRUDImpl
    }

    @Override
    @Transactional
    public UsuarioResponse cambiarRol(Long id, Rol newRole) {
        Usuario user = findOrThrow(id);
        if (newRole != Rol.ADMIN && isLastActiveAdmin(user)) {
            throw new LastAdminException("No se puede degradar al último administrador activo");
        }
        user.setRole(newRole);
        return userMapper.toResponse(guardar(user));   // ← heredado de ICRUDImpl
    }

    @Override
    @Transactional
    public UsuarioResponse cambiarEstado(Long id, boolean active) {
        Usuario user = findOrThrow(id);
        if (!active && isLastActiveAdmin(user)) {
            throw new LastAdminException("No se puede desactivar al último administrador activo");
        }
        user.setActive(active);
        return userMapper.toResponse(guardar(user));   // ← heredado de ICRUDImpl
    }

    /** ¿Este usuario es un ADMIN activo y, además, el único que queda? */
    private boolean isLastActiveAdmin(Usuario user) {
        return user.getRole() == Rol.ADMIN
                && user.isActive()
                && userRepository.countByRoleAndActiveTrue(Rol.ADMIN) <= 1;
    }

    private Usuario findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + id));
    }
}
