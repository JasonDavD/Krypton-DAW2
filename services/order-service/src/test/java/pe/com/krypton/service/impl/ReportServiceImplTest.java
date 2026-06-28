package pe.com.krypton.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import pe.com.krypton.client.CatalogClient;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPeriodoProjection;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;
import pe.com.krypton.dto.response.report.VentasTotalesProjection;
import pe.com.krypton.repository.ItemOrdenRepository;
import pe.com.krypton.repository.OrdenRepository;

/**
 * Unit test de los reportes locales (ventas + top productos). Mockea los repos: NO toca DB.
 * Cubre el armado del reporte, el mapeo robusto de Object[] (MySQL puede devolver BigDecimal
 * en SUM) y las validaciones de entrada.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private OrdenRepository ordenRepository;
    @Mock private ItemOrdenRepository itemOrdenRepository;
    @Mock private CatalogClient catalogClient;

    @InjectMocks private ReportServiceImpl reportService;

    @Test
    void should_build_ventas_report_when_dates_valid() {
        VentasPeriodoProjection fila = mock(VentasPeriodoProjection.class);
        when(fila.getPeriodo()).thenReturn(LocalDate.of(2024, 1, 5));
        when(fila.getOrdenes()).thenReturn(3L);
        when(fila.getMonto()).thenReturn(new BigDecimal("300.00"));
        VentasTotalesProjection tot = mock(VentasTotalesProjection.class);
        when(tot.getTotalOrdenes()).thenReturn(3L);
        when(tot.getTotalFacturado()).thenReturn(new BigDecimal("300.00"));
        when(tot.getTicketPromedio()).thenReturn(new BigDecimal("100.00"));
        when(ordenRepository.ventasPorPeriodo(eq("day"), any(), any())).thenReturn(List.of(fila));
        when(ordenRepository.ventasTotales(any(), any())).thenReturn(tot);

        VentasPorPeriodoReport report = reportService.ventasPorPeriodo(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "dia");

        assertThat(report.granularidad()).isEqualTo("dia");
        assertThat(report.totalOrdenes()).isEqualTo(3L);
        assertThat(report.totalFacturado()).isEqualByComparingTo("300.00");
        assertThat(report.filas()).singleElement().satisfies(f -> {
            assertThat(f.periodo()).isEqualTo(LocalDate.of(2024, 1, 5));
            assertThat(f.ordenes()).isEqualTo(3L);
        });
    }

    @Test
    void should_throw_when_granularidad_invalid() {
        assertThatThrownBy(() -> reportService.ventasPorPeriodo(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), "semana"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_ventas_desde_after_hasta() {
        assertThatThrownBy(() -> reportService.ventasPorPeriodo(
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1), "dia"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_map_top_productos_with_empty_sku_and_long_units() {
        // unidades viene como BigDecimal (como en MySQL) → debe mapearse a long.
        List<Object[]> rows = Collections.singletonList(
                new Object[]{1L, "Laptop", new BigDecimal("5"), new BigDecimal("5000.00")});
        when(itemOrdenRepository.findTopProductos(any(), any(), any(Pageable.class))).thenReturn(rows);

        TopProductosReport report = reportService.topProductos(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), 10);

        assertThat(report.productos()).singleElement().satisfies(p -> {
            assertThat(p.productId()).isEqualTo(1L);
            assertThat(p.sku()).isEmpty();
            assertThat(p.nombre()).isEqualTo("Laptop");
            assertThat(p.unidades()).isEqualTo(5L);
            assertThat(p.ingresos()).isEqualByComparingTo("5000.00");
        });
    }

    @Test
    void should_throw_when_limit_out_of_range() {
        assertThatThrownBy(() -> reportService.topProductos(null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_top_productos_partial_date_range() {
        assertThatThrownBy(() -> reportService.topProductos(LocalDate.of(2024, 1, 1), null, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_delegate_kardex_to_catalog_via_feign() {
        KardexReport expected = new KardexReport(1L, "KR-001", "Laptop", 10, null, null, List.of());
        when(catalogClient.kardex(eq(1L), any(), any())).thenReturn(expected);

        KardexReport result = reportService.kardexProducto(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).isSameAs(expected);
    }

    @Test
    void should_throw_when_kardex_partial_date_range() {
        assertThatThrownBy(() -> reportService.kardexProducto(1L, LocalDate.of(2024, 1, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
