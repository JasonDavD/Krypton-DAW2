package pe.com.krypton.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.com.krypton.dto.request.CreateUserRequest;
import pe.com.krypton.dto.response.UserResponse;
import pe.com.krypton.exception.DuplicateEmailException;
import pe.com.krypton.exception.LastAdminException;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.UserMapper;
import pe.com.krypton.model.User;
import pe.com.krypton.model.enums.Role;
import pe.com.krypton.repository.UserRepository;
import pe.com.krypton.service.impl.UserServiceImpl;

/** Unit test de la gestión de usuarios. La estrella: el guard del último admin. */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, passwordEncoder, new UserMapper());
    }

    private User user(Long id, Role role, boolean active) {
        User u = new User();
        u.setId(id);
        u.setName("U" + id);
        u.setEmail("u" + id + "@krypton.pe");
        u.setPassword("$2a$hash");
        u.setRole(role);
        u.setActive(active);
        u.setCreatedAt(Instant.now());
        return u;
    }

    @Test
    void should_list_users() {
        when(userRepository.findAll()).thenReturn(List.of(user(1L, Role.ADMIN, true), user(2L, Role.CLIENTE, true)));

        List<UserResponse> res = service.listAll();

        assertThat(res).hasSize(2);
        assertThat(res.get(0).email()).isEqualTo("u1@krypton.pe");
    }

    @Test
    void should_create_an_admin_when_email_is_new() {
        when(userRepository.existsByEmail("nuevo@krypton.pe")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("$2a$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(9L);
            return u;
        });

        UserResponse res = service.create(new CreateUserRequest("Nuevo", "nuevo@krypton.pe", "Secret123", Role.ADMIN));

        assertThat(res.role()).isEqualTo(Role.ADMIN);
        assertThat(res.active()).isTrue();
    }

    @Test
    void should_reject_create_when_email_exists() {
        when(userRepository.existsByEmail("dup@krypton.pe")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateUserRequest("X", "dup@krypton.pe", "x", Role.CLIENTE)))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void should_promote_client_to_admin() {
        User u = user(5L, Role.CLIENTE, true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = service.changeRole(5L, Role.ADMIN);

        assertThat(res.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void should_reject_demoting_the_last_active_admin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.ADMIN, true)));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(1L, Role.CLIENTE))
                .isInstanceOf(LastAdminException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void should_deactivate_a_client() {
        User u = user(7L, Role.CLIENTE, true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = service.setStatus(7L, false);

        assertThat(res.active()).isFalse();
    }

    @Test
    void should_reject_deactivating_the_last_active_admin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, Role.ADMIN, true)));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.setStatus(1L, false))
                .isInstanceOf(LastAdminException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void should_throw_not_found_when_user_missing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(99L, Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
