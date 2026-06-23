package pe.com.krypton.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.OrdenesListadoReport;
import pe.com.krypton.dto.response.report.TopProductoRow;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;
import pe.com.krypton.exception.ResourceNotFoundException;
import pe.com.krypton.mapper.OrderMapper;
import pe.com.krypton.model.Order;
import pe.com.krypton.model.Product;
import pe.com.krypton.model.StockMovement;
import pe.com.krypton.model.enums.MovementType;
import pe.com.krypton.model.enums.OrderStatus;
import pe.com.krypton.repository.OrderItemRepository;
import pe.com.krypton.repository.OrderRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.repository.StockMovementRepository;
import pe.com.krypton.repository.VentasPeriodoProjection;
import pe.com.krypton.repository.VentasTotalesProjection;
import pe.com.krypton.service.impl.ReportServiceImpl;

/**
 * Unit test de ReportServiceImpl. Repos MOCKEADOS. Sin Spring context, sin DB.
 * TDD: este test fue escrito RED antes de que exista ReportServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock ProductRepository productRepository;

    OrderMapper orderMapper;
    ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        orderMapper = new OrderMapper();
        service = new ReportServiceImpl(
                orderRepository,
                orderItemRepository,
                stockMovementRepository,
                productRepository,
                orderMapper);
    }

    // ─── Lima boundary math ─────────────────────────────────────────────────────

    /**
     * Lima is UTC-5. 2024-03-01 Lima midnight = 2024-03-01T05:00:00Z.
     * Next-day exclusive boundary: 2024-03-02T05:00:00Z.
     */
    @Test
    void ventasPorPeriodo_lima_boundary_start_and_end_instants() {
        LocalDate desde = LocalDate.of(2024, 3, 1);
        LocalDate hasta = LocalDate.of(2024, 3, 1);

        Instant expectedStart = Instant.parse("2024-03-01T05:00:00Z");
        Instant expectedEnd   = Instant.parse("2024-03-02T05:00:00Z");

        // stub totales
        VentasTotalesProjection totales = stubTotales(0L, BigDecimal.ZERO, BigDecimal.ZERO);
        when(orderRepository.ventasTotales(expectedStart, expectedEnd)).thenReturn(totales);
        when(orderRepository.ventasPorPeriodo(eq("day"), eq(expectedStart), eq(expectedEnd)))
                .thenReturn(List.of());

        service.ventasPorPeriodo(desde, hasta, "dia");

        // verify repo was called with the exact Lima-shifted Instants
        verify(orderRepository).ventasPorPeriodo(eq("day"), eq(expectedStart), eq(expectedEnd));
        verify(orderRepository).ventasTotales(eq(expectedStart), eq(expectedEnd));
    }

    // ─── gran mapping ───────────────────────────────────────────────────────────

    @Test
    void ventasPorPeriodo_gran_dia_maps_to_day() {
        LocalDate desde = LocalDate.of(2024, 1, 1);
        LocalDate hasta = LocalDate.of(2024, 1, 31);

        VentasTotalesProjection totales = stubTotales(0L, BigDecimal.ZERO, BigDecimal.ZERO);
        when(orderRepository.ventasTotales(any(), any())).thenReturn(totales);
        when(orderRepository.ventasPorPeriodo(eq("day"), any(), any())).thenReturn(List.of());

        service.ventasPorPeriodo(desde, hasta, "dia");

        verify(orderRepository).ventasPorPeriodo(eq("day"), any(Instant.class), any(Instant.class));
    }

    @Test
    void ventasPorPeriodo_gran_mes_maps_to_month() {
        LocalDate desde = LocalDate.of(2024, 1, 1);
        LocalDate hasta = LocalDate.of(2024, 3, 31);

        VentasTotalesProjection totales = stubTotales(0L, BigDecimal.ZERO, BigDecimal.ZERO);
        when(orderRepository.ventasTotales(any(), any())).thenReturn(totales);
        when(orderRepository.ventasPorPeriodo(eq("month"), any(), any())).thenReturn(List.of());

        service.ventasPorPeriodo(desde, hasta, "mes");

        verify(orderRepository).ventasPorPeriodo(eq("month"), any(Instant.class), any(Instant.class));
    }

    @Test
    void ventasPorPeriodo_invalid_gran_throws_illegal_argument() {
        assertThatThrownBy(() -> service.ventasPorPeriodo(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "semana"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("granularidad");
    }

    // ─── date range validation ───────────────────────────────────────────────────

    @Test
    void ventasPorPeriodo_desde_after_hasta_throws_illegal_argument() {
        assertThatThrownBy(() -> service.ventasPorPeriodo(
                LocalDate.of(2024, 3, 1), LocalDate.of(2024, 2, 28), "dia"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * REQ-RPT-07: "A same-day range (desde == hasta) is valid and means a single calendar day."
     * Asserts the service does NOT throw and returns a well-formed report for a single Lima day.
     */
    @Test
    void ventasPorPeriodo_sameDayRange_desdeEqualsHasta_isValid() {
        LocalDate sameDay = LocalDate.of(2025, 6, 15);

        VentasTotalesProjection totales = stubTotales(2L, new BigDecimal("80.00"), new BigDecimal("40.00"));
        when(orderRepository.ventasTotales(any(), any())).thenReturn(totales);

        VentasPeriodoProjection row = stubPeriodoRow(sameDay, 2L, new BigDecimal("80.00"));
        when(orderRepository.ventasPorPeriodo(eq("day"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(row));

        VentasPorPeriodoReport report = service.ventasPorPeriodo(sameDay, sameDay, "dia");

        assertThat(report).isNotNull();
        assertThat(report.totalOrdenes()).isEqualTo(2L);
        assertThat(report.filas()).hasSize(1);
        assertThat(report.filas().get(0).periodo()).isEqualTo(sameDay);
    }

    @Test
    void topProductos_desde_after_hasta_throws_illegal_argument() {
        assertThatThrownBy(() -> service.topProductos(
                LocalDate.of(2024, 3, 1), LocalDate.of(2024, 2, 1), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void topProductos_partial_date_range_desde_only_throws_illegal_argument() {
        assertThatThrownBy(() -> service.topProductos(
                LocalDate.of(2024, 1, 1), null, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void topProductos_partial_date_range_hasta_only_throws_illegal_argument() {
        assertThatThrownBy(() -> service.topProductos(
                null, LocalDate.of(2024, 1, 31), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kardex_partial_date_range_desde_only_throws_illegal_argument() {
        Product p = stubProduct(1L, "SKU-001", "Prod", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.kardexProducto(1L, LocalDate.of(2024, 1, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kardex_partial_date_range_hasta_only_throws_illegal_argument() {
        Product p = stubProduct(1L, "SKU-001", "Prod", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.kardexProducto(1L, null, LocalDate.of(2024, 1, 31)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listadoOrdenes_partial_date_range_desde_only_throws_illegal_argument() {
        assertThatThrownBy(() -> service.listadoOrdenes(
                null, LocalDate.of(2024, 1, 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listadoOrdenes_partial_date_range_hasta_only_throws_illegal_argument() {
        assertThatThrownBy(() -> service.listadoOrdenes(
                null, null, LocalDate.of(2024, 1, 31), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── limit validation ───────────────────────────────────────────────────────

    @Test
    void topProductos_limit_greater_than_100_throws_illegal_argument() {
        assertThatThrownBy(() -> service.topProductos(null, null, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void topProductos_limit_zero_throws_illegal_argument() {
        assertThatThrownBy(() -> service.topProductos(null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void topProductos_limit_negative_throws_illegal_argument() {
        assertThatThrownBy(() -> service.topProductos(null, null, -5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── topProductos limit forwarding ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void topProductos_no_dates_calls_findTopProductosSinFechas_with_correct_pageable() {
        TopProductoRow row = new TopProductoRow(1L, "SKU-01", "Prod A", 5L, BigDecimal.TEN);
        when(orderItemRepository.findTopProductosSinFechas(any(Pageable.class)))
                .thenReturn(List.of(row));

        TopProductosReport result = service.topProductos(null, null, 3);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(orderItemRepository).findTopProductosSinFechas(cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(3);
        assertThat(result.productos()).hasSize(1);
        assertThat(result.limit()).isEqualTo(3);
    }

    @SuppressWarnings("unchecked")
    @Test
    void topProductos_with_dates_calls_findTopProductos_with_correct_limit() {
        LocalDate desde = LocalDate.of(2024, 1, 1);
        LocalDate hasta = LocalDate.of(2024, 1, 31);

        TopProductoRow row = new TopProductoRow(2L, "SKU-02", "Prod B", 10L, new BigDecimal("100.00"));
        when(orderItemRepository.findTopProductos(any(Instant.class), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(row));

        TopProductosReport result = service.topProductos(desde, hasta, 5);

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(orderItemRepository).findTopProductos(any(), any(), cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(5);
        assertThat(result.productos()).hasSize(1);
    }

    // ─── kardex: product not found → 404 ───────────────────────────────────────

    @Test
    void kardex_product_not_found_throws_resource_not_found() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.kardexProducto(999L, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void kardex_product_found_no_dates_returns_all_movements() {
        Product p = stubProduct(1L, "SKU-001", "Prod A", 50);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        StockMovement sm = stubMovement(1L, p, MovementType.ENTRADA, 10);
        when(stockMovementRepository.findByProduct_IdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(sm));

        KardexReport report = service.kardexProducto(1L, null, null);

        assertThat(report.productId()).isEqualTo(1L);
        assertThat(report.sku()).isEqualTo("SKU-001");
        assertThat(report.stockActual()).isEqualTo(50);
        assertThat(report.movimientos()).hasSize(1);
        assertThat(report.movimientos().get(0).tipo()).isEqualTo("ENTRADA");
        assertThat(report.movimientos().get(0).cantidad()).isEqualTo(10);
    }

    @Test
    void kardex_with_dates_calls_between_method() {
        LocalDate desde = LocalDate.of(2024, 1, 1);
        LocalDate hasta = LocalDate.of(2024, 1, 31);

        Product p = stubProduct(1L, "SKU-001", "Prod", 20);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(stockMovementRepository.findByProduct_IdAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(1L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        KardexReport report = service.kardexProducto(1L, desde, hasta);

        assertThat(report.movimientos()).isEmpty();
        verify(stockMovementRepository)
                .findByProduct_IdAndCreatedAtBetweenOrderByCreatedAtAsc(eq(1L), any(), any());
    }

    // ─── listadoOrdenes ─────────────────────────────────────────────────────────

    @Test
    void listadoOrdenes_invalid_status_throws_illegal_argument() {
        assertThatThrownBy(() -> service.listadoOrdenes("UNKNOWN_STATUS", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @SuppressWarnings("unchecked")
    @Test
    void listadoOrdenes_no_filters_returns_all_orders() {
        when(orderRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of());

        OrdenesListadoReport report = service.listadoOrdenes(null, null, null, null);

        assertThat(report.ordenes()).isEmpty();
        assertThat(report.total()).isEqualTo(0L);
        assertThat(report.statusFiltro()).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void listadoOrdenes_valid_status_string_uses_enum_filter() {
        when(orderRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of());

        OrdenesListadoReport report = service.listadoOrdenes("CONFIRMADA", null, null, null);

        assertThat(report.statusFiltro()).isEqualTo("CONFIRMADA");
    }

    // ─── ventasPorPeriodo DTO assembly ──────────────────────────────────────────

    @Test
    void ventasPorPeriodo_assembles_report_with_summary_and_rows() {
        LocalDate desde = LocalDate.of(2024, 1, 1);
        LocalDate hasta = LocalDate.of(2024, 1, 31);

        VentasTotalesProjection totales = stubTotales(5L, new BigDecimal("250.00"), new BigDecimal("50.00"));
        when(orderRepository.ventasTotales(any(), any())).thenReturn(totales);

        VentasPeriodoProjection row = stubPeriodoRow(LocalDate.of(2024, 1, 10), 5L, new BigDecimal("250.00"));
        when(orderRepository.ventasPorPeriodo(any(), any(), any())).thenReturn(List.of(row));

        VentasPorPeriodoReport report = service.ventasPorPeriodo(desde, hasta, "dia");

        assertThat(report.totalOrdenes()).isEqualTo(5L);
        assertThat(report.totalFacturado()).isEqualByComparingTo("250.00");
        assertThat(report.ticketPromedio()).isEqualByComparingTo("50.00");
        assertThat(report.filas()).hasSize(1);
        assertThat(report.filas().get(0).periodo()).isEqualTo(LocalDate.of(2024, 1, 10));
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private VentasTotalesProjection stubTotales(long ordenes, BigDecimal facturado, BigDecimal ticket) {
        return new VentasTotalesProjection() {
            public long getTotalOrdenes()    { return ordenes; }
            public BigDecimal getTotalFacturado() { return facturado; }
            public BigDecimal getTicketPromedio() { return ticket; }
        };
    }

    private VentasPeriodoProjection stubPeriodoRow(LocalDate periodo, long ordenes, BigDecimal monto) {
        return new VentasPeriodoProjection() {
            public LocalDate getPeriodo() { return periodo; }
            public long getOrdenes()      { return ordenes; }
            public BigDecimal getMonto()  { return monto; }
        };
    }

    private Product stubProduct(Long id, String sku, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setSku(sku);
        p.setName(name);
        p.setStock(stock);
        return p;
    }

    private StockMovement stubMovement(Long id, Product product, MovementType type, int qty) {
        StockMovement sm = new StockMovement();
        sm.setId(id);
        sm.setProduct(product);
        sm.setType(type);
        sm.setQuantity(qty);
        sm.setReason("test reason");
        sm.setReference("REF-001");
        sm.setCreatedAt(Instant.now());
        return sm;
    }
}
