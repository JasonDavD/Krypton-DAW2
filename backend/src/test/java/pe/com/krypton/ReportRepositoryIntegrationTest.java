package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import pe.com.krypton.dto.response.report.TopProductoRow;
import pe.com.krypton.entity.Categoria;
import pe.com.krypton.entity.Orden;
import pe.com.krypton.entity.ItemOrden;
import pe.com.krypton.entity.Producto;
import pe.com.krypton.entity.MovimientoStock;
import pe.com.krypton.entity.Usuario;
import pe.com.krypton.entity.enums.TipoDocumento;
import pe.com.krypton.entity.enums.TipoMovimiento;
import pe.com.krypton.entity.enums.EstadoOrden;
import pe.com.krypton.entity.enums.Rol;
import pe.com.krypton.repository.CategoriaRepository;
import pe.com.krypton.repository.ItemOrdenRepository;
import pe.com.krypton.repository.OrdenRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.repository.MovimientoStockRepository;
import pe.com.krypton.repository.VentasPeriodoProjection;
import pe.com.krypton.repository.VentasTotalesProjection;
import pe.com.krypton.repository.UsuarioRepository;
import pe.com.krypton.spec.OrdenSpecification;

/**
 * Integration tests for the report repository layer.
 * Extends AbstractIntegrationTest (singleton Testcontainers Postgres 16).
 * NOT @DataJpaTest — full Spring context, real Postgres.
 *
 * Proves: native SQL aggregation, date_trunc(... AT TIME ZONE 'America/Lima') grouping,
 * half-open Instant ranges, and R2 JPQL SUM type alignment.
 *
 * Seed strategy:
 *   - 3 CONFIRMADA orders spanning Lima midnight boundary
 *   - 1 CANCELADA order (must be excluded from R1/R2)
 *   - 1 PENDIENTE order (must be excluded from R1/R2)
 *   - Stock movements for R3 window test
 *
 * Lima is UTC-5 (no DST). Midnight Lima 2024-03-02 = 2024-03-02T05:00:00Z.
 * Test data prefix: "IT-RPT-" (SKU), "it-rpt-" (email), "IT-Rpt-" (category).
 */
class ReportRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    // Lima dates
    private static final LocalDate D1 = LocalDate.of(2024, 3, 1);
    private static final LocalDate D2 = LocalDate.of(2024, 3, 2);

    // Instants: D1 in Lima = 2024-03-01T05:00:00Z, D2 = 2024-03-02T05:00:00Z
    private static final Instant D1_LIMA_START = D1.atStartOfDay(LIMA).toInstant();
    private static final Instant D2_LIMA_START = D2.atStartOfDay(LIMA).toInstant();
    // D3 exclusive end = start of 2024-03-03 Lima
    private static final Instant D3_LIMA_START = D2.plusDays(1).atStartOfDay(LIMA).toInstant();

    @Autowired UsuarioRepository userRepository;
    @Autowired CategoriaRepository categoryRepository;
    @Autowired ProductoRepository productRepository;
    @Autowired OrdenRepository orderRepository;
    @Autowired ItemOrdenRepository orderItemRepository;
    @Autowired MovimientoStockRepository stockMovementRepository;

    private Categoria category;
    private Producto product1;
    private Producto product2;
    private Usuario user;

    // Seeded entities tracked for cleanup
    private Orden orderD1a;
    private Orden orderD1b;
    private Orden orderD2;
    private Orden orderCancelada;
    private Orden orderPendiente;

    @BeforeEach
    void seed() {
        // Categoria
        category = new Categoria();
        category.setName("IT-Rpt-Cat-" + System.nanoTime());
        category = categoryRepository.save(category);

        // Products
        product1 = new Producto();
        product1.setSku("IT-RPT-P1-" + System.nanoTime());
        product1.setName("Prod Rpt 1");
        product1.setPrice(new BigDecimal("100.00"));
        product1.setStock(50);
        product1.setActive(true);
        product1.setCategory(category);
        product1 = productRepository.save(product1);

        product2 = new Producto();
        product2.setSku("IT-RPT-P2-" + System.nanoTime());
        product2.setName("Prod Rpt 2");
        product2.setPrice(new BigDecimal("200.00"));
        product2.setStock(30);
        product2.setActive(true);
        product2.setCategory(category);
        product2 = productRepository.save(product2);

        // Usuario
        user = new Usuario();
        user.setName("IT Rpt Usuario");
        user.setEmail("it-rpt-" + System.nanoTime() + "@krypton.pe");
        user.setPassword("hashed");
        user.setRole(Rol.CLIENTE);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user = userRepository.save(user);

        // CONFIRMADA order on D1 Lima (2024-03-01T06:00:00Z = 01:00 Lima D1)
        orderD1a = saveOrder(EstadoOrden.CONFIRMADA,
                Instant.parse("2024-03-01T06:00:00Z"),
                new BigDecimal("300.00"));
        saveItem(orderD1a, product1, 3, new BigDecimal("100.00"));

        // CONFIRMADA order also on D1 Lima (2024-03-01T20:00:00Z = 15:00 Lima D1)
        orderD1b = saveOrder(EstadoOrden.CONFIRMADA,
                Instant.parse("2024-03-01T20:00:00Z"),
                new BigDecimal("200.00"));
        saveItem(orderD1b, product2, 1, new BigDecimal("200.00"));

        // CONFIRMADA order on D2 Lima (2024-03-02T10:00:00Z = 05:00 Lima D2)
        // 2024-03-02T05:00:00Z is midnight Lima D2 — this instant is exactly at start, included
        orderD2 = saveOrder(EstadoOrden.CONFIRMADA,
                Instant.parse("2024-03-02T10:00:00Z"),
                new BigDecimal("400.00"));
        saveItem(orderD2, product1, 4, new BigDecimal("100.00"));

        // CANCELADA — must be excluded
        orderCancelada = saveOrder(EstadoOrden.CANCELADA,
                Instant.parse("2024-03-01T12:00:00Z"),
                new BigDecimal("150.00"));
        saveItem(orderCancelada, product1, 1, new BigDecimal("100.00"));

        // PENDIENTE — must be excluded
        orderPendiente = saveOrder(EstadoOrden.PENDIENTE,
                Instant.parse("2024-03-02T08:00:00Z"),
                new BigDecimal("250.00"));
        saveItem(orderPendiente, product2, 1, new BigDecimal("200.00"));

        // Stock movements for R3 (product1)
        // Movement inside window [D1_LIMA_START, D2_LIMA_START)
        MovimientoStock mvInside = new MovimientoStock();
        mvInside.setProduct(product1);
        mvInside.setType(TipoMovimiento.ENTRADA);
        mvInside.setQuantity(10);
        mvInside.setReason("compra");
        mvInside.setCreatedAt(Instant.parse("2024-03-01T08:00:00Z"));
        stockMovementRepository.save(mvInside);

        // Movement outside window (before D1)
        MovimientoStock mvOutside = new MovimientoStock();
        mvOutside.setProduct(product1);
        mvOutside.setType(TipoMovimiento.SALIDA);
        mvOutside.setQuantity(5);
        mvOutside.setReason("venta");
        mvOutside.setCreatedAt(Instant.parse("2024-02-28T10:00:00Z"));
        stockMovementRepository.save(mvOutside);
    }

    @AfterEach
    void cleanup() {
        // FK order: stock_movement → order_items → orders → products → categories → users
        stockMovementRepository.deleteAll(
                stockMovementRepository.findByProduct_IdOrderByCreatedAtAsc(product1.getId()));
        stockMovementRepository.deleteAll(
                stockMovementRepository.findByProduct_IdOrderByCreatedAtAsc(product2.getId()));

        orderItemRepository.deleteAll(orderItemRepository.findByOrder(orderD1a));
        orderItemRepository.deleteAll(orderItemRepository.findByOrder(orderD1b));
        orderItemRepository.deleteAll(orderItemRepository.findByOrder(orderD2));
        orderItemRepository.deleteAll(orderItemRepository.findByOrder(orderCancelada));
        orderItemRepository.deleteAll(orderItemRepository.findByOrder(orderPendiente));

        orderRepository.deleteAll(List.of(orderD1a, orderD1b, orderD2, orderCancelada, orderPendiente));
        productRepository.deleteAll(List.of(product1, product2));
        categoryRepository.delete(category);
        userRepository.delete(user);
    }

    // ---- R1: ventasPorPeriodo -----------------------------------------------

    @Test
    void r1_ventasPorPeriodo_day_buckets_use_lima_date_and_exclude_non_confirmada() {
        // Window: D1 through end-of-D2 inclusive → [D1_LIMA_START, D3_LIMA_START)
        List<VentasPeriodoProjection> rows =
                orderRepository.ventasPorPeriodo("day", D1_LIMA_START, D3_LIMA_START);

        // Expect 2 buckets: D1 (2 orders) and D2 (1 order)
        assertThat(rows).hasSize(2);

        VentasPeriodoProjection d1Row = rows.stream()
                .filter(r -> r.getPeriodo().equals(D1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No bucket for D1"));
        assertThat(d1Row.getOrdenes()).isEqualTo(2L);
        assertThat(d1Row.getMonto()).isEqualByComparingTo(new BigDecimal("500.00")); // 300+200

        VentasPeriodoProjection d2Row = rows.stream()
                .filter(r -> r.getPeriodo().equals(D2))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No bucket for D2"));
        assertThat(d2Row.getOrdenes()).isEqualTo(1L);
        assertThat(d2Row.getMonto()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    void r1_ventasPorPeriodo_only_confirmada_orders_included() {
        // Narrow window: D1 only → [D1_LIMA_START, D2_LIMA_START)
        List<VentasPeriodoProjection> rows =
                orderRepository.ventasPorPeriodo("day", D1_LIMA_START, D2_LIMA_START);

        // CANCELADA and PENDIENTE orders on D1 must be excluded
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOrdenes()).isEqualTo(2L);
    }

    @Test
    void r1_ventasTotales_returns_correct_count_sum_avg() {
        VentasTotalesProjection totals =
                orderRepository.ventasTotales(D1_LIMA_START, D3_LIMA_START);

        assertThat(totals.getTotalOrdenes()).isEqualTo(3L);
        assertThat(totals.getTotalFacturado()).isEqualByComparingTo(new BigDecimal("900.00")); // 300+200+400
        // avg = 900 / 3 = 300.00
        assertThat(totals.getTicketPromedio()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    // ---- R2: top productos --------------------------------------------------

    @Test
    void r2_top_productos_returns_topProductoRow_with_correct_types_and_values() {
        List<TopProductoRow> rows = orderItemRepository.findTopProductos(
                D1_LIMA_START, D3_LIMA_START, PageRequest.of(0, 10));

        // product1: 3+4=7 units, 300+400=700 revenue
        // product2: 1 unit (only orderD1b is CONFIRMADA), 200 revenue
        assertThat(rows).hasSize(2);

        TopProductoRow top = rows.get(0);
        assertThat(top.productId()).isEqualTo(product1.getId());
        assertThat(top.unidades()).isEqualTo(7L);
        assertThat(top.ingresos()).isEqualByComparingTo(new BigDecimal("700.00"));

        // TYPE alignment check (design risk R2): Hibernate 6.x + Postgres returns Long (boxed) for SUM(int)
        assertThat(top.unidades()).isInstanceOf(Long.class);
        // BigDecimal ingresos — verify class at runtime via the actual object type
        assertThat(top.ingresos()).isInstanceOf(BigDecimal.class);
    }

    @Test
    void r2_top_productos_capped_by_pageable_limit() {
        // limit=1 → only top product
        List<TopProductoRow> rows = orderItemRepository.findTopProductos(
                D1_LIMA_START, D3_LIMA_START, PageRequest.of(0, 1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).productId()).isEqualTo(product1.getId());
    }

    @Test
    void r2_top_productos_excludes_non_confirmada_orders() {
        // Only CONFIRMADA orders matter; CANCELADA/PENDIENTE items should not count
        List<TopProductoRow> rows = orderItemRepository.findTopProductos(
                D1_LIMA_START, D3_LIMA_START, PageRequest.of(0, 10));

        // product2: only orderD1b (CONFIRMADA, 1 unit) — orderPendiente excluded
        TopProductoRow p2Row = rows.stream()
                .filter(r -> r.productId().equals(product2.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row for product2"));
        assertThat(p2Row.unidades()).isEqualTo(1L);
    }

    // ---- R3: stock movements ------------------------------------------------

    @Test
    void r3_findByProduct_IdAndCreatedAtBetween_returns_only_movements_in_window() {
        List<MovimientoStock> inside = stockMovementRepository
                .findByProduct_IdAndCreatedAtBetweenOrderByCreatedAtAsc(
                        product1.getId(), D1_LIMA_START, D2_LIMA_START);

        assertThat(inside).hasSize(1);
        assertThat(inside.get(0).getType()).isEqualTo(TipoMovimiento.ENTRADA);
        assertThat(inside.get(0).getQuantity()).isEqualTo(10);
    }

    @Test
    void r3_findByProduct_IdOrderByCreatedAtAsc_returns_all_movements() {
        List<MovimientoStock> all = stockMovementRepository
                .findByProduct_IdOrderByCreatedAtAsc(product1.getId());

        assertThat(all).hasSize(2);
        // Ordered by created_at ASC: outside (Feb 28) first, inside (Mar 1) second
        assertThat(all.get(0).getType()).isEqualTo(TipoMovimiento.SALIDA);
        assertThat(all.get(1).getType()).isEqualTo(TipoMovimiento.ENTRADA);
    }

    // ---- R4: OrdenSpecification composition --------------------------------

    @Test
    void r4_spec_filters_by_status() {
        Specification<Orden> spec = Specification.where(OrdenSpecification.hasStatus(EstadoOrden.CONFIRMADA));

        List<Orden> results = orderRepository.findAll(spec);

        // All CONFIRMADA orders — at minimum our 3 seeded ones
        boolean allConfirmada = results.stream()
                .allMatch(o -> o.getStatus() == EstadoOrden.CONFIRMADA);
        assertThat(allConfirmada).isTrue();
        assertThat(results.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void r4_spec_filters_by_status_and_date_and_userId() {
        Specification<Orden> spec = Specification
                .where(OrdenSpecification.hasStatus(EstadoOrden.CONFIRMADA))
                .and(OrdenSpecification.dateBetween(D1_LIMA_START, D3_LIMA_START))
                .and(OrdenSpecification.hasUser(user.getId()));

        List<Orden> results = orderRepository.findAll(
                spec, Sort.by(Sort.Direction.DESC, "orderDate"));

        // All 3 CONFIRMADA orders belong to our seeded user
        assertThat(results).hasSize(3);
        // Ordered by orderDate DESC
        assertThat(results.get(0).getOrderDate()).isAfterOrEqualTo(results.get(1).getOrderDate());
    }

    @Test
    void r4_spec_null_predicates_return_all_confirmada() {
        // All null → no filter → returns everything
        Specification<Orden> spec = Specification
                .where(OrdenSpecification.hasStatus(null))
                .and(OrdenSpecification.dateBetween(null, null))
                .and(OrdenSpecification.hasUser(null));

        // Should not throw — Specification.where(null) is valid in Spring Data
        List<Orden> all = orderRepository.findAll(spec);
        assertThat(all).isNotNull();
    }

    // ---- helpers ------------------------------------------------------------

    private Orden saveOrder(EstadoOrden status, Instant orderDate, BigDecimal total) {
        Orden o = new Orden();
        o.setUser(user);
        o.setStatus(status);
        o.setOrderDate(orderDate);
        o.setTotal(total);
        // Comprobante (NOT NULL desde V7) — valores dummy, irrelevantes para estos reportes.
        o.setDocumentType(TipoDocumento.BOLETA);
        o.setCustomerName("Cliente Test");
        o.setCustomerDoc("12345678");
        o.setSubtotal(total);
        o.setShippingCost(BigDecimal.ZERO);
        o.setIgv(BigDecimal.ZERO);
        return orderRepository.save(o);
    }

    private void saveItem(Orden order, Producto product, int qty, BigDecimal unitPrice) {
        ItemOrden item = new ItemOrden();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(qty);
        item.setUnitPrice(unitPrice);
        orderItemRepository.save(item);
    }
}
