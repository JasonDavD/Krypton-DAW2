package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.Categoria;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.entity.Usuario;
import pe.com.krypton.entity.enums.Rol;
import pe.com.krypton.repository.CarritoRepository;
import pe.com.krypton.repository.CategoriaRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.repository.UsuarioRepository;

/**
 * Cierra los escenarios de rechazo de constraints del spec persistence-schema:
 * unicidad (sku), integridad referencial (FK inválida) y 1 carrito por usuario.
 *
 * @Transactional → cada test hace ROLLBACK al terminar, así no ensucia la base
 * compartida (singleton container) ni rompe los counts de los otros tests.
 */
@Transactional
class ConstraintsIntegrationTest extends AbstractIntegrationTest {

    @Autowired UsuarioRepository users;
    @Autowired CategoriaRepository categories;
    @Autowired ProductoRepository products;
    @Autowired CarritoRepository carts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void rejects_duplicate_sku() {
        Categoria cat = categories.saveAndFlush(newCategory());
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
        Usuario u = users.saveAndFlush(newUser());
        carts.saveAndFlush(newCart(u));

        assertThatThrownBy(() -> carts.saveAndFlush(newCart(u)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ----- helpers -----

    private Categoria newCategory() {
        Categoria c = new Categoria();
        c.setName("Cat-" + System.nanoTime());
        return c;
    }

    private Producto newProduct(String sku, Categoria cat) {
        Producto p = new Producto();
        p.setSku(sku);
        p.setName("Producto");
        p.setPrice(new BigDecimal("10.00"));
        p.setStock(0);
        p.setActive(true);
        p.setCategory(cat);
        return p;
    }

    private Usuario newUser() {
        Usuario u = new Usuario();
        u.setName("Test");
        u.setEmail("user-" + System.nanoTime() + "@krypton.pe");
        u.setPassword("x");
        u.setRole(Rol.CLIENTE);
        u.setCreatedAt(Instant.now());
        return u;
    }

    private Carrito newCart(Usuario u) {
        Carrito c = new Carrito();
        c.setUser(u);
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        return c;
    }
}
