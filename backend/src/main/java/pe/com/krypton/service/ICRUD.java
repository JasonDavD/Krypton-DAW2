package pe.com.krypton.service;

import java.util.List;

/**
 * Contrato CRUD genérico reutilizable. {@code T} = entidad, {@code ID} = tipo de la PK.
 *
 * <p>Trabaja a nivel de ENTIDAD. Cada service concreto lo extiende para heredar las
 * operaciones de persistencia y mapea Entity ↔ DTO dentro de su propia implementación.
 */
public interface ICRUD<T, ID> {

    T registrar(T entidad);

    T actualizar(T entidad);

    void eliminar(ID id);

    List<T> listar();

    T buscarPorId(ID id);
}
