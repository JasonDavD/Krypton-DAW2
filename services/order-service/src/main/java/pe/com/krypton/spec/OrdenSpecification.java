package pe.com.krypton.spec;

import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;
import pe.com.krypton.entity.Orden;
import pe.com.krypton.entity.enums.EstadoOrden;

/**
 * Fábricas de predicados JPA para filtrar órdenes (listado admin).
 * Contrato null-predicate: cada método retorna {@code null} cuando el filtro está ausente,
 * para componer limpio con {@code Specification.where().and()}.
 */
public final class OrdenSpecification {

    private OrdenSpecification() {}

    /** Filtro por estado. Null cuando {@code status} es null. */
    public static Specification<Orden> hasStatus(EstadoOrden status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Filtro por rango de fecha [start, end) (half-open). Null cuando ambos son null.
     * Solo start → ge; solo end → lt; ambos → ge AND lt.
     */
    public static Specification<Orden> dateBetween(Instant start, Instant end) {
        if (start == null && end == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (start != null && end != null) {
                return cb.and(
                        cb.greaterThanOrEqualTo(root.get("orderDate"), start),
                        cb.lessThan(root.get("orderDate"), end));
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("orderDate"), start);
            } else {
                return cb.lessThan(root.get("orderDate"), end);
            }
        };
    }
}
