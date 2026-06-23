package pe.com.krypton.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pe.com.krypton.model.User;
import pe.com.krypton.model.enums.Role;

/**
 * Unit test puro: JwtService no tiene dependencias mockeables (firma simétrica),
 * se instancia directo con un secreto de test fijo. Sin Spring, sin DB.
 */
class JwtServiceTest {

    private static final String SECRET = "test-secret-key-krypton-jwt-1234567890-abcdef"; // ≥32 bytes
    private static final long ONE_DAY = 86_400_000L;

    private final JwtService jwt = new JwtService(SECRET, ONE_DAY);

    private User user(String email, Role role) {
        User u = new User();
        u.setName("Test");
        u.setEmail(email);
        u.setPassword("hash");
        u.setRole(role);
        return u;
    }

    @Test
    void should_generate_a_valid_token_when_user_is_given() {
        String token = jwt.generateToken(user("ana@krypton.pe", Role.CLIENTE));

        assertThat(jwt.isValid(token)).isTrue();
    }

    @Test
    void should_extract_email_from_a_generated_token() {
        String token = jwt.generateToken(user("ana@krypton.pe", Role.ADMIN));

        assertThat(jwt.extractEmail(token)).isEqualTo("ana@krypton.pe");
    }

    @Test
    void should_reject_an_expired_token() {
        JwtService expiredIssuer = new JwtService(SECRET, -1000L); // exp en el pasado
        String token = expiredIssuer.generateToken(user("ana@krypton.pe", Role.CLIENTE));

        assertThat(jwt.isValid(token)).isFalse();
    }

    @Test
    void should_reject_a_token_signed_with_another_secret() {
        JwtService other = new JwtService("another-totally-different-secret-key-987654321", ONE_DAY);
        String token = other.generateToken(user("ana@krypton.pe", Role.CLIENTE));

        assertThat(jwt.isValid(token)).isFalse();
    }

    @Test
    void should_reject_a_garbage_token() {
        assertThat(jwt.isValid("not-a-jwt")).isFalse();
    }
}
