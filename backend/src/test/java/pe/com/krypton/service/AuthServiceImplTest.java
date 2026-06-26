package pe.com.krypton.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.com.krypton.dto.request.LoginRequest;
import pe.com.krypton.dto.request.RegisterRequest;
import pe.com.krypton.dto.response.AuthResponse;
import pe.com.krypton.dto.response.UsuarioResponse;
import pe.com.krypton.exception.DuplicateEmailException;
import pe.com.krypton.exception.InvalidCredentialsException;
import pe.com.krypton.mapper.UsuarioMapper;
import pe.com.krypton.entity.Usuario;
import pe.com.krypton.entity.enums.Rol;
import pe.com.krypton.repository.UsuarioRepository;
import pe.com.krypton.security.JwtService;
import pe.com.krypton.service.impl.AuthServiceImpl;

/**
 * Unit test de la lógica de auth: repo, encoder y JwtService MOCKEADOS (sin Spring,
 * sin DB). Por eso el service es interface-based: se mockea la dependencia.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock UsuarioRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, passwordEncoder, jwtService, new UsuarioMapper(), 86_400_000L);
    }

    private Usuario client(String hash, boolean active) {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setName("Ana");
        u.setEmail("ana@krypton.pe");
        u.setPassword(hash);
        u.setRole(Rol.CLIENTE);
        u.setActive(active);
        u.setCreatedAt(Instant.now());
        return u;
    }

    @Test
    void should_register_a_client_with_hashed_password_when_email_is_new() {
        when(userRepository.existsByEmail("ana@krypton.pe")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("$2a$hash");
        when(userRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        UsuarioResponse res = service.register(new RegisterRequest("Ana", "ana@krypton.pe", "Secret123"));

        assertThat(res.role()).isEqualTo(Rol.CLIENTE);
        assertThat(res.active()).isTrue();
        ArgumentCaptor<Usuario> saved = ArgumentCaptor.forClass(Usuario.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("$2a$hash"); // hasheado, nunca plano
    }

    @Test
    void should_reject_register_when_email_already_exists() {
        when(userRepository.existsByEmail("ana@krypton.pe")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Ana", "ana@krypton.pe", "Secret123")))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void should_return_token_when_login_credentials_are_valid() {
        Usuario user = client("$2a$hash", true);
        when(userRepository.findByEmail("ana@krypton.pe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "$2a$hash")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse res = service.login(new LoginRequest("ana@krypton.pe", "Secret123"));

        assertThat(res.token()).isEqualTo("jwt-token");
        assertThat(res.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void should_reject_login_when_password_is_wrong() {
        Usuario user = client("$2a$hash", true);
        when(userRepository.findByEmail("ana@krypton.pe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@krypton.pe", "bad")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void should_reject_login_when_user_is_inactive() {
        Usuario user = client("$2a$hash", false);
        when(userRepository.findByEmail("ana@krypton.pe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@krypton.pe", "Secret123")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtService, never()).generateToken(any());
    }
}
