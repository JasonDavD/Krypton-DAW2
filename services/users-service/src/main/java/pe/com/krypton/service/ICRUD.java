package pe.com.krypton.service;

import java.util.List;

/**
 * Contrato CRUD genérico reutilizable. {@code T} = entidad, {@code ID} = tipo de la PK.
 *
 * <p>Trabaja a nivel de ENTIDAD (no DTO). Cada service concreto lo extiende para heredar
 * estas operaciones de persistencia y las usa INTERNAMENTE; hacia afuera expone sus
 * métodos de dominio (que reciben/devuelven DTOs y hacen el mapeo). Los nombres acá
 * (guardar/borrar/...) se eligen para no chocar con la API pública en DTOs
 * (registrar/eliminar/listar/buscarPorId).
 */
public interface ICRUD<T, ID> {

    T guardar(T entidad);

    void borrar(ID id);

    List<T> listarTodos();

    T obtenerPorId(ID id);
}
