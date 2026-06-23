package pe.com.krypton.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import pe.com.krypton.model.Product;

/**
 * Unit test para ProductSpecification.
 * Verifica: (a) null-predicate contract cuando el filtro está ausente,
 * (b) que el predicado correcto se construye cuando el filtro está presente.
 */
@ExtendWith(MockitoExtension.class)
class ProductSpecificationTest {

    @Mock Root<Product> root;
    @Mock CriteriaQuery<?> query;
    @Mock CriteriaBuilder cb;

    // ---- nameLike --------------------------------------------------------

    @Test
    void nameLike_returns_null_when_filter_absent() {
        Specification<Product> spec = ProductSpecification.nameLike(null);
        assertThat(spec).isNull();
    }

    @Test
    void nameLike_returns_null_when_filter_blank() {
        Specification<Product> spec = ProductSpecification.nameLike("   ");
        assertThat(spec).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void nameLike_builds_like_predicate_when_filter_present() {
        Path<String> namePath = mock(Path.class);
        Expression<String> lowerExpr = mock(Expression.class);
        Predicate predicate = mock(Predicate.class);

        when(root.<String>get("name")).thenReturn(namePath);
        when(cb.lower(namePath)).thenReturn(lowerExpr);
        when(cb.like(eq(lowerExpr), any(String.class))).thenReturn(predicate);

        Specification<Product> spec = ProductSpecification.nameLike("teclado");
        assertThat(spec).isNotNull();

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isEqualTo(predicate);
        verify(cb).like(eq(lowerExpr), eq("%teclado%"));
    }

    // ---- hasCategory -----------------------------------------------------

    @Test
    void hasCategory_returns_null_when_filter_absent() {
        Specification<Product> spec = ProductSpecification.hasCategory(null);
        assertThat(spec).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasCategory_builds_equal_predicate_when_filter_present() {
        Path<Object> categoryPath = mock(Path.class);
        Path<Object> categoryIdPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.get("category")).thenReturn(categoryPath);
        when(categoryPath.get("id")).thenReturn(categoryIdPath);
        when(cb.equal(categoryIdPath, 5L)).thenReturn(predicate);

        Specification<Product> spec = ProductSpecification.hasCategory(5L);
        assertThat(spec).isNotNull();

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isEqualTo(predicate);
        verify(cb).equal(categoryIdPath, 5L);
    }

    // ---- priceBetween ----------------------------------------------------

    @Test
    void priceBetween_returns_null_when_both_absent() {
        assertThat(ProductSpecification.priceBetween(null, null)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void priceBetween_builds_ge_predicate_when_only_min_present() {
        Path<BigDecimal> pricePath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.<BigDecimal>get("price")).thenReturn(pricePath);
        when(cb.greaterThanOrEqualTo(pricePath, new BigDecimal("10.00"))).thenReturn(predicate);

        Specification<Product> spec = ProductSpecification.priceBetween(new BigDecimal("10.00"), null);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isEqualTo(predicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void priceBetween_builds_le_predicate_when_only_max_present() {
        Path<BigDecimal> pricePath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.<BigDecimal>get("price")).thenReturn(pricePath);
        when(cb.lessThanOrEqualTo(pricePath, new BigDecimal("100.00"))).thenReturn(predicate);

        Specification<Product> spec = ProductSpecification.priceBetween(null, new BigDecimal("100.00"));
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isEqualTo(predicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void priceBetween_builds_between_predicate_when_both_present() {
        Path<BigDecimal> pricePath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.<BigDecimal>get("price")).thenReturn(pricePath);
        when(cb.between(pricePath, new BigDecimal("10.00"), new BigDecimal("100.00"))).thenReturn(predicate);

        Specification<Product> spec = ProductSpecification.priceBetween(new BigDecimal("10.00"), new BigDecimal("100.00"));
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isEqualTo(predicate);
    }

    // ---- isActive --------------------------------------------------------

    @Test
    void isActive_returns_null_when_filter_absent() {
        assertThat(ProductSpecification.isActive(null)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void isActive_builds_equal_predicate_when_filter_present() {
        Path<Boolean> activePath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);

        when(root.<Boolean>get("active")).thenReturn(activePath);
        when(cb.equal(activePath, true)).thenReturn(predicate);

        Specification<Product> spec = ProductSpecification.isActive(true);
        assertThat(spec).isNotNull();

        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isEqualTo(predicate);
    }
}
