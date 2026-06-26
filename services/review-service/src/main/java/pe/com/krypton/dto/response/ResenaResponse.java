package pe.com.krypton.dto.response;

import java.time.Instant;

/** Una resena tal como se devuelve en la API. */
public record ResenaResponse(
        Long id,
        String userEmail,
        int rating,
        String comment,
        Instant createdAt) {
}
