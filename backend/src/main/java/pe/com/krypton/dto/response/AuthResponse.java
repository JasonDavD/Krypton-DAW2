package pe.com.krypton.dto.response;

/** Respuesta del login: el token y sus metadatos. */
public record AuthResponse(String token, String tokenType, long expiresIn) {
}
