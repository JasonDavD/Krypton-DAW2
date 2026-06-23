package pe.com.krypton.service.impl;

import java.time.Instant;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.dto.request.CreateUserRequest;
import pe.com.krypton.dto.response.UserResponse;
import pe.com.krypton.exception.DuplicateEmailException;
import pe.com.krypton.exception.LastAdminException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.UserMapper;
import pe.com.krypton.model.User;
import pe.com.krypton.model.enums.Role;
import pe.com.krypton.repository.UserRepository;
import pe.com.krypton.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepository.findAll().stream().map(userMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("El email ya está registrado");
        }
        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role()); // rol elegible: el ADMIN puede crear otro ADMIN
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse changeRole(Long id, Role newRole) {
        User user = findOrThrow(id);
        if (newRole != Role.ADMIN && isLastActiveAdmin(user)) {
            throw new LastAdminException("No se puede degradar al último administrador activo");
        }
        user.setRole(newRole);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse setStatus(Long id, boolean active) {
        User user = findOrThrow(id);
        if (!active && isLastActiveAdmin(user)) {
            throw new LastAdminException("No se puede desactivar al último administrador activo");
        }
        user.setActive(active);
        return userMapper.toResponse(userRepository.save(user));
    }

    /** ¿Este usuario es un ADMIN activo y, además, el único que queda? */
    private boolean isLastActiveAdmin(User user) {
        return user.getRole() == Role.ADMIN
                && user.isActive()
                && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1;
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + id));
    }
}
