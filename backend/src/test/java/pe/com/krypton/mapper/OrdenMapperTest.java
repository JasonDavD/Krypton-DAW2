package pe.com.krypton.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.com.krypton.dto.response.ItemOrdenResponse;
import pe.com.krypton.dto.response.OrdenResponse;
import pe.com.krypton.entity.Orden;
import pe.com.krypton.entity.ItemOrden;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.entity.Usuario;
import pe.com.krypton.entity.enums.TipoDocumento;
import pe.com.krypton.entity.enums.EstadoOrden;

/**
 * Unit test for OrdenMapper. No Spring context, no DB (pure Java).
 * Verifies: subtotal computation, snapshot invariant, total source, status as String, userId.
 * Satisfies REQ-OM-04 / ADR-5.
 */
class OrdenMapperTest {

    OrdenMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OrdenMapper();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Usuario user(Long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setEmail("test@krypton.pe");
        return u;
    }

    private Producto product(Long id, String name, BigDecimal currentPrice) {
        Producto p = new Producto();
        p.setId(id);
        p.setName(name);
        p.setPrice(currentPrice);
        p.setStock(100);
        p.setActive(true);
        return p;
    }

    private Orden order(Long id, Usuario user, BigDecimal total, EstadoOrden status) {
        Orden o = new Orden();
        o.setId(id);
        o.setUser(user);
        o.setTotal(total);
        o.setStatus(status);
        o.setOrderDate(Instant.now());
        // Comprobante + desglose (el mapper los lee; documentType.name() NPE si es null)
        o.setDocumentType(TipoDocumento.BOLETA);
        o.setCustomerName("Juan Cliente");
        o.setCustomerDoc("12345678");
        o.setSubtotal(total);
        o.setShippingCost(BigDecimal.ZERO);
        o.setIgv(BigDecimal.ZERO);
        return o;
    }

    private ItemOrden orderItem(Long id, Orden order, Producto product, int qty, BigDecimal unitPrice) {
        ItemOrden item = new ItemOrden();
        item.setId(id);
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(qty);
        item.setUnitPrice(unitPrice);
        return item;
    }

    // ─── toItemResponse ─────────────────────────────────────────────────────────

    @Test
    void toItemResponse_subtotal_equals_unitPrice_times_quantity() {
        Usuario u = user(3L);
        Orden o = order(1L, u, new BigDecimal("5999.80"), EstadoOrden.PENDIENTE);
        Producto p = product(12L, "Notebook", new BigDecimal("2999.90"));
        ItemOrden item = orderItem(1L, o, p, 2, new BigDecimal("2999.90"));

        ItemOrdenResponse resp = mapper.toItemResponse(item);

        assertThat(resp.id()).isEqualTo(1L);
        assertThat(resp.productId()).isEqualTo(12L);
        assertThat(resp.productName()).isEqualTo("Notebook");
        assertThat(resp.quantity()).isEqualTo(2);
        assertThat(resp.unitPrice()).isEqualByComparingTo(new BigDecimal("2999.90"));
        assertThat(resp.subtotal()).isEqualByComparingTo(new BigDecimal("5999.80"));
    }

    @Test
    void toItemResponse_subtotal_uses_snapshot_not_current_price() {
        // The snapshot unitPrice = 100.00, but the product.price is now 150.00
        Usuario u = user(3L);
        Orden o = order(1L, u, new BigDecimal("100.00"), EstadoOrden.PENDIENTE);
        Producto p = product(12L, "Notebook", new BigDecimal("150.00")); // price updated later
        ItemOrden item = orderItem(1L, o, p, 1, new BigDecimal("100.00")); // snapshot at purchase

        ItemOrdenResponse resp = mapper.toItemResponse(item);

        // subtotal must use the unitPrice snapshot (100.00), NOT the current product price (150.00)
        assertThat(resp.unitPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(resp.subtotal()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // ─── toResponse ─────────────────────────────────────────────────────────────

    @Test
    void toResponse_total_comes_from_order_not_recomputed() {
        Usuario u = user(3L);
        // order.total = 999.00 (persisted snapshot)
        Orden o = order(1L, u, new BigDecimal("999.00"), EstadoOrden.PENDIENTE);
        Producto p = product(12L, "Notebook", new BigDecimal("500.00"));
        // two items × 500.00 = 1000.00 — but we want to confirm mapper uses order.total, not recomputed
        ItemOrden item1 = orderItem(1L, o, p, 1, new BigDecimal("499.00"));
        ItemOrden item2 = orderItem(2L, o, p, 1, new BigDecimal("500.00"));

        OrdenResponse resp = mapper.toResponse(o, List.of(item1, item2));

        // total MUST come from order.getTotal(), not sum of items
        assertThat(resp.total()).isEqualByComparingTo(new BigDecimal("999.00"));
    }

    @Test
    void toResponse_status_is_enum_name_string() {
        Usuario u = user(3L);
        Orden o = order(1L, u, BigDecimal.TEN, EstadoOrden.CONFIRMADA);

        OrdenResponse resp = mapper.toResponse(o, List.of());

        assertThat(resp.status()).isEqualTo("CONFIRMADA");
    }

    @Test
    void toResponse_userId_equals_order_user_id() {
        Usuario u = user(42L);
        Orden o = order(7L, u, BigDecimal.TEN, EstadoOrden.PENDIENTE);

        OrdenResponse resp = mapper.toResponse(o, List.of());

        assertThat(resp.userId()).isEqualTo(42L);
    }

    @Test
    void toResponse_maps_all_items_and_preserves_order() {
        Usuario u = user(3L);
        Orden o = order(1L, u, new BigDecimal("350.00"), EstadoOrden.PENDIENTE);
        Producto p1 = product(10L, "Laptop", new BigDecimal("300.00"));
        Producto p2 = product(11L, "Mouse", new BigDecimal("50.00"));
        ItemOrden item1 = orderItem(1L, o, p1, 1, new BigDecimal("300.00"));
        ItemOrden item2 = orderItem(2L, o, p2, 1, new BigDecimal("50.00"));

        OrdenResponse resp = mapper.toResponse(o, List.of(item1, item2));

        assertThat(resp.items()).hasSize(2);
        assertThat(resp.items().get(0).productName()).isEqualTo("Laptop");
        assertThat(resp.items().get(1).productName()).isEqualTo("Mouse");
    }

    @Test
    void toResponse_maps_comprobante_and_desglose() {
        Usuario u = user(3L);
        Orden o = order(1L, u, new BigDecimal("120.00"), EstadoOrden.PENDIENTE);
        o.setDocumentType(TipoDocumento.FACTURA);
        o.setCustomerName("ACME SAC");
        o.setCustomerDoc("20512345678");
        o.setSubtotal(new BigDecimal("100.00"));
        o.setShippingCost(new BigDecimal("20.00"));
        o.setIgv(new BigDecimal("18.31"));

        OrdenResponse resp = mapper.toResponse(o, List.of());

        assertThat(resp.documentType()).isEqualTo("FACTURA"); // enum.name() — desacoplado del wire
        assertThat(resp.customerName()).isEqualTo("ACME SAC");
        assertThat(resp.customerDoc()).isEqualTo("20512345678");
        assertThat(resp.subtotal()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(resp.shippingCost()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(resp.igv()).isEqualByComparingTo(new BigDecimal("18.31"));
    }
}
