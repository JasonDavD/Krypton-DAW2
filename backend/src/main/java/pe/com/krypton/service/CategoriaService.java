package pe.com.krypton.service;

import java.util.List;
import pe.com.krypton.dto.request.CategoriaRequest;
import pe.com.krypton.dto.response.CategoriaResponse;

/** Operaciones de catálogo para categorías. */
public interface CategoriaService {

    /** Lista todas las categorías disponibles. */
    List<CategoriaResponse> list();

    /** Retorna la categoría o lanza ResourceNotFoundException (404). */
    CategoriaResponse getById(Long id);

    /** Crea una categoría. Nombre único → DuplicateCategoryNameException (409). */
    CategoriaResponse create(CategoriaRequest request);

    /**
     * Actualiza una categoría. La unicidad del nombre excluye el propio id
     * (permite que el mismo nombre se reenvíe sin rechazo).
     */
    CategoriaResponse update(Long id, CategoriaRequest request);

    /**
     * Elimina la categoría (hard delete).
     * Guard: si hay productos que la referencian → CategoryInUseException (409).
     * El guard se evalúa ANTES de cualquier operación de escritura.
     */
    void delete(Long id);
}
