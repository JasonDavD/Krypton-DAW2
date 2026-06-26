package pe.com.krypton.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import pe.com.krypton.entity.Orden;
import pe.com.krypton.entity.enums.EstadoOrden;

/**
 * Unit tests para OrdenSpecification.
 * Verifica: (a) null-predicate contract cuando el filtro está ausente,
 * (b) que el predicado correcto se construye cuando el filtro está presente.
 * Mirrors ProductSpecificationTest — no Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class OrdenSpecificationTest {

    @Mock Root<Orden> root;
    @Mock CriteriaQuery<?> query;
    @Mock CriteriaBuilder cb;

    // ---- hasStatus -----------------------------------------------------------

    @Test
    void hasStatus_returns_null_when_status_is_null() {
        Specification<Orden> spec = OrdenSpecification.hasStatus(null);
        assertThat(spec).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasStatus_builds_equal_predicate_when_status_present() {
        Path<EstadoOrden> statusPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.<EstadoOrden>get("status")).thenReturn(statusPath);
        when(cb.equal(statusPath, EstadoOrden.CONFIRMADA)).thenReturn(predicate);

        Specification<Orden> spec = OrdenSpecification.hasStatus(EstadoOrden.CONFIRMADA);
        assertThat(spec).isNotNull();

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isEqualTo(predicate);
        verify(cb).equal(statusPath, EstadoOrden.CONFIRMADA);
    }

    // ---- dateBetween ---------------------------------------------------------

    @Test
    void dateBetween_returns_null_when_both_bounds_are_null() {
        Specification<Orden> spec = OrdenSpecification.dateBetween(null, null);
        assertThat(spec).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void dateBetween_builds_ge_predicate_when_only_start_present() {
        Path<Instant> datePath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        Instant start = Instant.parse("2024-03-01T05:00:00Z");

        when(root.<Instant>get("orderDate")).thenReturn(datePath);
        when(cb.greaterThanOrEqualTo(datePath, start)).thenReturn(predicate);

        Specification<Orden> spec = OrdenSpecification.dateBetween(start, null);
        assertThat(spec).isNotNull();

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isEqualTo(predicate);
        verify(cb).greaterThanOrEqualTo(datePath, start);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dateBetween_builds_le_predicate_when_only_end_present() {
        Path<Instant> datePath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        Instant end = Instant.parse("2024-03-02T05:00:00Z");

        when(root.<Instant>get("orderDate")).thenReturn(datePath);
        when(cb.lessThan(datePath, end)).thenReturn(predicate);

        Specification<Orden> spec = OrdenSpecification.dateBetween(null, end);
        assertThat(spec).isNotNull();

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isEqualTo(predicate);
        verify(cb).lessThan(datePath, end);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dateBetween_builds_between_predicate_when_both_present() {
        Path<Instant> datePath = mock(Path.class);
        Predicate gePredicate = mock(Predicate.class);
        Predicate ltPredicate = mock(Predicate.class);
        Predicate andPredicate = mock(Predicate.class);
        Instant start = Instant.parse("2024-03-01T05:00:00Z");
        Instant end = Instant.parse("2024-03-02T05:00:00Z");

        when(root.<Instant>get("orderDate")).thenReturn(datePath);
        when(cb.greaterThanOrEqualTo(datePath, start)).thenReturn(gePredicate);
        when(cb.lessThan(datePath, end)).thenReturn(ltPredicate);
        when(cb.and(gePredicate, ltPredicate)).thenReturn(andPredicate);

        Specification<Orden> spec = OrdenSpecification.dateBetween(start, end);
        assertThat(spec).isNotNull();

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isEqualTo(andPredicate);
        verify(cb).greaterThanOrEqualTo(datePath, start);
        verify(cb).lessThan(datePath, end);
    }

    // ---- hasUser -------------------------------------------------------------

    @Test
    void hasUser_returns_null_when_userId_is_null() {
        Specification<Orden> spec = OrdenSpecification.hasUser(null);
        assertThat(spec).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasUser_builds_equal_predicate_when_userId_present() {
        Path<Object> userPath = mock(Path.class);
        Path<Object> userIdPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get("user")).thenReturn(userPath);
        when(userPath.get("id")).thenReturn(userIdPath);
        when(cb.equal(userIdPath, 1L)).thenReturn(predicate);

        Specification<Orden> spec = OrdenSpecification.hasUser(1L);
        assertThat(spec).isNotNull();

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isEqualTo(predicate);
        verify(cb).equal(userIdPath, 1L);
    }
}
