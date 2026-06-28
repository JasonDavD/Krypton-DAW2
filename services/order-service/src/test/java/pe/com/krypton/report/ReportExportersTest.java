package pe.com.krypton.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.com.krypton.dto.response.report.KardexMovimientoRow;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.TopProductoRow;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPeriodoRow;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;

/** Verifica que los exporters generan binarios NO vacíos para cada reporte (PDF y XLSX). */
class ReportExportersTest {

    private final ReportPdfExporter pdf = new ReportPdfExporter();
    private final ReportExcelExporter excel = new ReportExcelExporter();

    private static VentasPorPeriodoReport ventas() {
        return new VentasPorPeriodoReport(Instant.EPOCH, Instant.EPOCH, "dia", 1,
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                List.of(new VentasPeriodoRow(LocalDate.of(2024, 1, 1), 1, new BigDecimal("100.00"))));
    }

    private static TopProductosReport top() {
        return new TopProductosReport(Instant.EPOCH, Instant.EPOCH, 10,
                List.of(new TopProductoRow(1L, "", "Laptop", 5L, new BigDecimal("5000.00"))));
    }

    private static KardexReport kardex() {
        return new KardexReport(1L, "KR-001", "Laptop", 7, Instant.EPOCH, Instant.EPOCH,
                List.of(new KardexMovimientoRow(Instant.EPOCH, "SALIDA", 2, "x", "ORDER-1")));
    }

    @Test
    void pdf_exports_are_non_empty() {
        assertThat(pdf.exportVentas(ventas())).isNotEmpty();
        assertThat(pdf.exportTopProductos(top())).isNotEmpty();
        assertThat(pdf.exportKardex(kardex())).isNotEmpty();
    }

    @Test
    void excel_exports_are_non_empty() {
        assertThat(excel.exportVentas(ventas())).isNotEmpty();
        assertThat(excel.exportTopProductos(top())).isNotEmpty();
        assertThat(excel.exportKardex(kardex())).isNotEmpty();
    }
}
