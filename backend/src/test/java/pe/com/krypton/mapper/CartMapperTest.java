package pe.com.krypton.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.com.krypton.dto.response.CartItemResponse;
import pe.com.krypton.dto.response.CartResponse;
import pe.com.krypton.model.Cart;
import pe.com.krypton.model.CartItem;
import pe.com.krypton.model.Product;
import pe.com.krypton.model.User;

/**
 * Unit test de CartMapper. Sin Spring context, sin DB.
 * TDD: RED — escrito antes de que exista CartMapper.
 */
class CartMapperTest {

    CartMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CartMapper();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Product product(Long id, String sku, String name, BigDecimal price) {
        Product p = new Product();
        p.setId(id);
        p.setSku(sku);
        p.setName(name);
        p.setPrice(price);
        p.setStock(100);
        p.setActive(true);
        return p;
    }

    private CartItem cartItem(Long id, Cart cart, Product product, int qty) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(qty);
        return item;
    }

    private Cart cart(Long id) {
        Cart c = new Cart();
        c.setId(id);
        User u = new User();
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
        Cart c = cart(1L);
        Product p = product(10L, "SKU-001", "Laptop Pro", new BigDecimal("999.90"));
        CartItem item = cartItem(5L, c, p, 2);

        CartItemResponse resp = mapper.toItemResponse(item);

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
        Cart c = cart(1L);
        Product p1 = product(10L, "SKU-001", "Laptop Pro", new BigDecimal("999.90"));
        Product p2 = product(11L, "SKU-002", "Mouse", new BigDecimal("50.00"));
        CartItem item1 = cartItem(5L, c, p1, 2);  // subtotal = 1999.80
        CartItem item2 = cartItem(6L, c, p2, 3);  // subtotal = 150.00

        CartResponse resp = mapper.toResponse(c, List.of(item1, item2));

        assertThat(resp.cartId()).isEqualTo(1L);
        assertThat(resp.items()).hasSize(2);
        assertThat(resp.total()).isEqualByComparingTo(new BigDecimal("2149.80"));
        assertThat(resp.updatedAt()).isNotNull();
    }

    @Test
    void toResponse_with_empty_items_has_zero_total() {
        Cart c = cart(1L);

        CartResponse resp = mapper.toResponse(c, List.of());

        assertThat(resp.cartId()).isEqualTo(1L);
        assertThat(resp.items()).isEmpty();
        assertThat(resp.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── emptyCart ──────────────────────────────────────────────────────────────

    @Test
    void emptyCart_has_null_cartId_empty_items_zero_total_null_updatedAt() {
        CartResponse resp = mapper.emptyCart();

        assertThat(resp.cartId()).isNull();
        assertThat(resp.items()).isEmpty();
        assertThat(resp.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.updatedAt()).isNull();
    }
}
