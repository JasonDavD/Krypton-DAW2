package pe.com.krypton.service;

import java.util.List;
import pe.com.krypton.dto.request.CategoriaRequest;
import pe.com.krypton.dto.response.CategoriaResponse;

/** Operaciones de catálogo para categorías. */
public interface CategoriaService {

    /** Lista todas las categorías disponibles. */
    List<CategoriaResponse> listar();

    /** Retorna la categoría o lanza ResourceNotFoundException (404). */
    CategoriaResponse buscarPorId(Long id);

    /** Crea una categoría. Nombre único → DuplicateCategoryNameException (409). */
    CategoriaResponse registrar(CategoriaRequest request);

    /**
     * Actualiza una categoría. La unicidad del nombre excluye el propio id
     * (permite que el mismo nombre se reenvíe sin rechazo).
     */
    CategoriaResponse actualizar(Long id, CategoriaRequest request);

    /**
     * Elimina la categoría (hard delete).
     * Guard: si hay productos que la referencian → CategoryInUseException (409).
     * El guard se evalúa ANTES de cualquier operación de escritura.
     */
    void eliminar(Long id);
}
