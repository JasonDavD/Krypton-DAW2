package pe.com.krypton.mapper;

import org.springframework.stereotype.Component;
import pe.com.krypton.dto.response.CategoryResponse;
import pe.com.krypton.entity.Categoria;

/** Traduce la entidad Categoria a su DTO de salida. */
@Component
public class CategoriaMapper {

    public CategoryResponse toResponse(Categoria category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription());
    }
}
