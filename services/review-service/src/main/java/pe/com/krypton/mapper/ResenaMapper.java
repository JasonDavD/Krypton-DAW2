package pe.com.krypton.mapper;

import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.ResenaResponse;
import pe.com.krypton.entity.Resena;

/** Mapper manual Entity -> DTO (nunca exponemos la @Entity en la API). */
@Component
public class ResenaMapper {

    public ResenaResponse toResponse(Resena resena) {
        return new ResenaResponse(
                resena.getId(),
                resena.getUserEmail(),
                resena.getRating(),
                resena.getComment(),
                resena.getCreatedAt());
    }
}
