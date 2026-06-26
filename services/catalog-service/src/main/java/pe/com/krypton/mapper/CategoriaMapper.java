package pe.com.krypton.mapper;

import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.CategoriaResponse;
import pe.com.krypton.entity.Categoria;

/** Traduce la entidad Categoria a su DTO de salida. */
@Component
public class CategoriaMapper {

    public CategoriaResponse toResponse(Categoria category) {
        return new CategoriaResponse(
                category.getId(),
                category.getName(),
                category.getDescription());
    }
}
