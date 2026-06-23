package pe.com.krypton.spec;

import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;
import pe.com.krypton.model.Order;
import pe.com.krypton.model.enums.OrderStatus;

/**
 * Fábricas de predicados JPA para filtrado de órdenes.
 * Contrato null-predicate: cada método retorna {@code null} cuando el filtro está ausente,
 * permitiendo composición limpia con {@code Specification.where().and()}.
 * Mirrors {@link ProductSpecification}.
 */
public final class OrderSpecification {

    private OrderSpecification() {}

    /**
     * Filtro por estado de orden.
     * Null cuando {@code status} es null.
     */
    public static Specification<Order> hasStatus(OrderStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Filtro por rango de fecha de orden (half-open: [start, end)).
     * Null cuando ambos límites son null.
     * Si sólo {@code start} está presente → ge. Si sólo {@code end} → lt.
     * Si ambos presentes → ge AND lt.
     */
    public static Specification<Order> dateBetween(Instant start, Instant end) {
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

    /**
     * Filtro por usuario propietario de la orden.
     * Null cuando {@code userId} es null.
     */
    public static Specification<Order> hasUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }
}
