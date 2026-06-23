package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.model.Cart;
import pe.com.krypton.model.Category;
import pe.com.krypton.model.Product;
import pe.com.krypton.model.User;
import pe.com.krypton.model.enums.Role;
import pe.com.krypton.repository.CartRepository;
import pe.com.krypton.repository.CategoryRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.repository.UserRepository;

/**
 * Cierra los escenarios de rechazo de constraints del spec persistence-schema:
 * unicidad (sku), integridad referencial (FK inválida) y 1 carrito por usuario.
 *
 * @Transactional → cada test hace ROLLBACK al terminar, así no ensucia la base
 * compartida (singleton container) ni rompe los counts de los otros tests.
 */
@Transactional
class ConstraintsIntegrationTest extends AbstractIntegrationTest {

    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired CartRepository carts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void rejects_duplicate_sku() {
        Category cat = categories.saveAndFlush(newCategory());
        products.saveAndFlush(newProduct("SKU-DUP", cat));

        assertThatThrownBy(() -> products.saveAndFlush(newProduct("SKU-DUP", cat)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejects_product_with_nonexistent_category() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO products (sku, name, price, stock, active, category_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "SKU-GHOST", "Ghost", new BigDecimal("9.99"), 0, true, 999_999L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejects_second_cart_for_same_user() {
        User u = users.saveAndFlush(newUser());
        carts.saveAndFlush(newCart(u));

        assertThatThrownBy(() -> carts.saveAndFlush(newCart(u)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ----- helpers -----

    private Category newCategory() {
        Category c = new Category();
        c.setName("Cat-" + System.nanoTime());
        return c;
    }

    private Product newProduct(String sku, Category cat) {
        Product p = new Product();
        p.setSku(sku);
        p.setName("Producto");
        p.setPrice(new BigDecimal("10.00"));
        p.setStock(0);
        p.setActive(true);
        p.setCategory(cat);
        return p;
    }

    private User newUser() {
        User u = new User();
        u.setName("Test");
        u.setEmail("user-" + System.nanoTime() + "@krypton.pe");
        u.setPassword("x");
        u.setRole(Role.CLIENTE);
        u.setCreatedAt(Instant.now());
        return u;
    }

    private Cart newCart(User u) {
        Cart c = new Cart();
        c.setUser(u);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        return c;
    }
}
