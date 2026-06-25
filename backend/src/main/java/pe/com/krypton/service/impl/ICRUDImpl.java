package pe.com.krypton.service.impl;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.service.ICRUD;

/**
 * Implementación abstracta del CRUD genérico. Se apoya en {@link #repo()}, que cada
 * service concreto provee con su propio repository.
 *
 * <p>Usa {@link ResourceNotFoundException} (404 vía el handler global) en lugar de un
 * {@code throws Exception} genérico: las firmas quedan limpias y específicas.
 */
public abstract class ICRUDImpl<T, ID> implements ICRUD<T, ID> {

    /** Cada service concreto declara cuál es su repository. */
    protected abstract JpaRepository<T, ID> repo();

    @Override
    public T registrar(T entidad) {
        return repo().save(entidad);
    }

    @Override
    public T actualizar(T entidad) {
        return repo().save(entidad);
    }

    @Override
    public void eliminar(ID id) {
        if (!repo().existsById(id)) {
            throw new ResourceNotFoundException("Registro con id " + id + " no existe");
        }
        repo().deleteById(id);
    }

    @Override
    public List<T> listar() {
        return repo().findAll();
    }

    @Override
    public T buscarPorId(ID id) {
        return repo().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registro con id " + id + " no existe"));
    }
}
