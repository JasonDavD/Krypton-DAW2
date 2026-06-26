package pe.com.krypton.spec;

import java.math.BigDecimal;
import org.springframework.data.jpa.domain.Specification;
import pe.com.krypton.entity.Producto;

/**
 * Fábricas de predicados JPA para búsqueda de productos.
 * Contrato null-predicate: cada método retorna {@code null} cuando el filtro está ausente,
 * permitiendo composición limpia con {@code Specification.where().and()}.
 */
public final class ProductoSpecification {

    private ProductoSpecification() {}

    /** Búsqueda por nombre (LIKE case-insensitive). Null cuando {@code name} es null o blank. */
    public static Specification<Producto> nameLike(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    /** Filtro por categoría. Null cuando {@code categoryId} es null. */
    public static Specification<Producto> hasCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) ->
                cb.equal(root.get("category").get("id"), categoryId);
    }

    /**
     * Filtro por rango de precio.
     * Null cuando ambos límites son null.
     * Si sólo uno está presente se aplica ge o le según corresponda.
     */
    public static Specification<Producto> priceBetween(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("price"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("price"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("price"), max);
            }
        };
    }

    /** Filtro por estado activo. Null cuando {@code active} es null. */
    public static Specification<Producto> isActive(Boolean active) {
        if (active == null) {
            return null;
        }
        return (root, query, cb) ->
                cb.equal(root.get("active"), active);
    }
}
