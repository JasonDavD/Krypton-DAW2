package pe.com.krypton.security;
import io.jsonwebtoken.Claims; import io.jsonwebtoken.JwtException; import io.jsonwebtoken.Jws; import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys; import java.nio.charset.StandardCharsets; import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service;
@Service
public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    public boolean isValid(String token) { try { parse(token); return true; } catch (JwtException | IllegalArgumentException e) { return false; } }
    public String extractEmail(String token) { return parse(token).getPayload().getSubject(); }
    public String extractRole(String token) { return parse(token).getPayload().get("role", String.class); }
    private Jws<Claims> parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token); }
}
