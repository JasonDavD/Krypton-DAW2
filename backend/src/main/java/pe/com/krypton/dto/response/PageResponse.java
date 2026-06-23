package pe.com.krypton.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Envuelve {@link Page} en un contrato estable que no expone internos de Spring Data.
 * Usar {@link #of(Page, List)} para construir desde una page ya mapeada a DTOs.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
