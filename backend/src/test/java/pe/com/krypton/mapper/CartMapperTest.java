package pe.com.krypton.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.com.krypton.dto.response.ItemCarritoResponse;
import pe.com.krypton.dto.response.CarritoResponse;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.ItemCarrito;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.entity.Usuario;

/**
 * Unit test de CarritoMapper. Sin Spring context, sin DB.
 * TDD: RED — escrito antes de que exista CarritoMapper.
 */
class CartMapperTest {

    CarritoMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CarritoMapper();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Producto product(Long id, String sku, String name, BigDecimal price) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setName(name);
        p.setPrice(price);
        p.setStock(100);
        p.setActive(true);
        return p;
    }

    private ItemCarrito cartItem(Long id, Carrito cart, Producto product, int qty) {
        ItemCarrito item = new ItemCarrito();
        item.setId(id);
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(qty);
        return item;
    }

    private Carrito cart(Long id) {
        Carrito c = new Carrito();
        c.setId(id);
        Usuario u = new Usuario();
        u.setId(1L);
        c.setUser(u);
        Instant now = Instant.now();
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return c;
    }

    // ─── toItemResponse ─────────────────────────────────────────────────────────

    @Test
    void toItemResponse_subtotal_equals_price_times_quantity() {
        Carrito c = cart(1L);
        Producto p = product(10L, "SKU-001", "Laptop Pro", new BigDecimal("999.90"));
        ItemCarrito item = cartItem(5L, c, p, 2);

        ItemCarritoResponse resp = mapper.toItemResponse(item);

        assertThat(resp.itemId()).isEqualTo(5L);
        assertThat(resp.productId()).isEqualTo(10L);
        assertThat(resp.productName()).isEqualTo("Laptop Pro");
        assertThat(resp.sku()).isEqualTo("SKU-001");
        assertThat(resp.price()).isEqualByComparingTo(new BigDecimal("999.90"));
        assertThat(resp.quantity()).isEqualTo(2);
        assertThat(resp.subtotal()).isEqualByComparingTo(new BigDecimal("1999.80"));
    }

    // ─── toResponse ─────────────────────────────────────────────────────────────

    @Test
    void toResponse_total_equals_sum_of_all_subtotals() {
        Carrito c = cart(1L);
        Producto p1 = product(10L, "SKU-001", "Laptop Pro", new BigDecimal("999.90"));
        Producto p2 = product(11L, "SKU-002", "Mouse", new BigDecimal("50.00"));
        ItemCarrito item1 = cartItem(5L, c, p1, 2);  // subtotal = 1999.80
        ItemCarrito item2 = cartItem(6L, c, p2, 3);  // subtotal = 150.00

        CarritoResponse resp = mapper.toResponse(c, List.of(item1, item2));

        assertThat(resp.cartId()).isEqualTo(1L);
        assertThat(resp.items()).hasSize(2);
        assertThat(resp.total()).isEqualByComparingTo(new BigDecimal("2149.80"));
        assertThat(resp.updatedAt()).isNotNull();
    }

    @Test
    void toResponse_with_empty_items_has_zero_total() {
        Carrito c = cart(1L);

        CarritoResponse resp = mapper.toResponse(c, List.of());

        assertThat(resp.cartId()).isEqualTo(1L);
        assertThat(resp.items()).isEmpty();
        assertThat(resp.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── emptyCart ──────────────────────────────────────────────────────────────

    @Test
    void emptyCart_has_null_cartId_empty_items_zero_total_null_updatedAt() {
        CarritoResponse resp = mapper.emptyCart();

        assertThat(resp.cartId()).isNull();
        assertThat(resp.items()).isEmpty();
        assertThat(resp.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.updatedAt()).isNull();
    }
}
