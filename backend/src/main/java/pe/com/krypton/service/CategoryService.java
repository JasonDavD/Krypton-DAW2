package pe.com.krypton.service;

import java.util.List;
import pe.com.krypton.dto.request.CategoryRequest;
import pe.com.krypton.dto.response.CategoryResponse;

/** Operaciones de catálogo para categorías. */
public interface CategoryService {

    /** Lista todas las categorías disponibles. */
    List<CategoryResponse> list();

    /** Retorna la categoría o lanza ResourceNotFoundException (404). */
    CategoryResponse getById(Long id);

    /** Crea una categoría. Nombre único → DuplicateCategoryNameException (409). */
    CategoryResponse create(CategoryRequest request);

    /**
     * Actualiza una categoría. La unicidad del nombre excluye el propio id
     * (permite que el mismo nombre se reenvíe sin rechazo).
     */
    CategoryResponse update(Long id, CategoryRequest request);

    /**
     * Elimina la categoría (hard delete).
     * Guard: si hay productos que la referencian → CategoryInUseException (409).
     * El guard se evalúa ANTES de cualquier operación de escritura.
     */
    void delete(Long id);
}
