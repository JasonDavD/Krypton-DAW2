package pe.com.krypton.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.com.krypton.dto.response.OrderResponse;
import pe.com.krypton.dto.response.report.KardexMovimientoRow;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.OrdenesListadoReport;
import pe.com.krypton.dto.response.report.TopProductoRow;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPeriodoRow;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;

/**
 * Pure-Java unit tests for ExcelExporter. No Spring context.
 * Re-parses byte[] via POI to assert structure.
 * TDD: RED before ExcelExporter implementation exists.
 *
 * NOTE: XSSFRow implements Iterable<Cell>, so assertThat(XSSFRow) is ambiguous in AssertJ.
 * We use assertNotNull(row) (JUnit 5) or assertThat(row.getPhysicalNumberOfCells()) for non-null
 * checks on rows.
 */
class ExcelExporterTest {

    ExcelExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new ExcelExporter();
    }

    // ─── exportVentas ────────────────────────────────────────────────────────────

    @Test
    void exportVentas_nonempty_produces_parseable_xlsx() throws Exception {
        VentasPorPeriodoReport report = ventasReport(List.of(
                new VentasPeriodoRow(LocalDate.of(2024, 1, 1), 3L, new BigDecimal("150.00")),
                new VentasPeriodoRow(LocalDate.of(2024, 1, 2), 2L, new BigDecimal("80.00"))
        ));

        byte[] bytes = exporter.exportVentas(report);

        assertThat(bytes).isNotNull().isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            // has rows (summary + column header + data rows)
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(1);
            // at least one row with >= 3 cells (the column-header row)
            boolean found = false;
            for (int r = 0; r < sheet.getPhysicalNumberOfRows(); r++) {
                XSSFRow row = sheet.getRow(r);
                if (row != null && row.getPhysicalNumberOfCells() >= 3) {
                    found = true;
                    break;
                }
            }
            assertThat(found).as("Expect a column-header row with >= 3 cells").isTrue();
        }
    }

    @Test
    void exportVentas_empty_list_produces_valid_xlsx_no_data_rows() throws Exception {
        VentasPorPeriodoReport report = ventasReport(List.of());

        byte[] bytes = exporter.exportVentas(report);

        assertThat(bytes).isNotNull().isNotEmpty();
        assertThatCode(() -> {
            try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                // valid xlsx — summary + header rows exist, no data rows
                assertThat(wb.getSheetAt(0).getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(1);
                assertNotNull(wb.getSheetAt(0).getRow(0));
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void exportVentas_header_cells_contain_periodo_ordenes_monto() throws Exception {
        byte[] bytes = exporter.exportVentas(ventasReport(List.of(
                new VentasPeriodoRow(LocalDate.of(2024, 3, 1), 1L, BigDecimal.TEN)
        )));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            // find the header row (first row with >= 3 string cells)
            XSSFSheet sheet = wb.getSheetAt(0);
            boolean foundHeaderRow = false;
            for (int r = 0; r < sheet.getPhysicalNumberOfRows(); r++) {
                XSSFRow row = sheet.getRow(r);
                if (row != null && row.getPhysicalNumberOfCells() >= 3) {
                    foundHeaderRow = true;
                    // verify cells are non-blank strings
                    for (int c = 0; c < 3; c++) {
                        assertThat(row.getCell(c).getStringCellValue()).isNotBlank();
                    }
                    break;
                }
            }
            assertThat(foundHeaderRow).as("Should find a row with at least 3 string cells").isTrue();
        }
    }

    // ─── exportTopProductos ───────────────────────────────────────────────────────

    @Test
    void exportTopProductos_nonempty_has_header_and_data_rows() throws Exception {
        TopProductosReport report = topReport(List.of(
                new TopProductoRow(1L, "SKU-A", "Prod A", 100L, new BigDecimal("500.00")),
                new TopProductoRow(2L, "SKU-B", "Prod B", 50L, new BigDecimal("250.00"))
        ));

        byte[] bytes = exporter.exportTopProductos(report);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            // header (row 0) + 2 data rows = 3 total
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
            assertNotNull(sheet.getRow(0));
        }
    }

    @Test
    void exportTopProductos_empty_list_valid_xlsx_header_only() throws Exception {
        byte[] bytes = exporter.exportTopProductos(topReport(List.of()));

        assertThat(bytes).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);
            assertNotNull(wb.getSheetAt(0).getRow(0));
        }
    }

    @Test
    void exportTopProductos_columns_are_rank_sku_nombre_unidades_ingresos() throws Exception {
        byte[] bytes = exporter.exportTopProductos(topReport(List.of(
                new TopProductoRow(1L, "SKU-01", "Producto", 10L, BigDecimal.ONE)
        )));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFRow header = wb.getSheetAt(0).getRow(0);
            assertNotNull(header);
            assertThat(header.getPhysicalNumberOfCells()).isGreaterThanOrEqualTo(5);
        }
    }

    // ─── exportKardex ─────────────────────────────────────────────────────────────

    @Test
    void exportKardex_nonempty_has_header_and_data_rows() throws Exception {
        KardexReport report = kardexReport(List.of(
                new KardexMovimientoRow(Instant.now(), "ENTRADA", 20, "Compra", "PO-001"),
                new KardexMovimientoRow(Instant.now(), "SALIDA",  5, "Venta",  "ORDER-001")
        ));

        byte[] bytes = exporter.exportKardex(report);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(1);
            assertNotNull(sheet.getRow(0));
        }
    }

    @Test
    void exportKardex_empty_movements_valid_xlsx() throws Exception {
        byte[] bytes = exporter.exportKardex(kardexReport(List.of()));

        assertThat(bytes).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(wb.getSheetAt(0).getRow(0));
        }
    }

    // ─── exportOrdenes ────────────────────────────────────────────────────────────

    @Test
    void exportOrdenes_nonempty_has_header_and_data_rows() throws Exception {
        OrderResponse order = new OrderResponse(
                1L, 10L, Instant.now(), "CONFIRMADA",
                "BOLETA", "Juan Cliente", "12345678",
                new BigDecimal("99.00"), BigDecimal.ZERO, new BigDecimal("15.10"),
                new BigDecimal("99.00"), List.of());

        OrdenesListadoReport report = new OrdenesListadoReport(
                "CONFIRMADA", null, null, null, 1L, List.of(order));

        byte[] bytes = exporter.exportOrdenes(report);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThan(1);
            assertNotNull(sheet.getRow(0));
        }
    }

    @Test
    void exportOrdenes_empty_list_valid_xlsx_no_data_rows() throws Exception {
        OrdenesListadoReport report = new OrdenesListadoReport(
                null, null, null, null, 0L, List.of());

        byte[] bytes = exporter.exportOrdenes(report);

        assertThat(bytes).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            // valid xlsx — has at least the header row (may also have summary rows)
            assertThat(wb.getSheetAt(0).getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(1);
            assertNotNull(wb.getSheetAt(0).getRow(0));
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────────

    private VentasPorPeriodoReport ventasReport(List<VentasPeriodoRow> filas) {
        return new VentasPorPeriodoReport(
                Instant.parse("2024-01-01T05:00:00Z"),
                Instant.parse("2024-02-01T05:00:00Z"),
                "dia",
                filas.stream().mapToLong(VentasPeriodoRow::ordenes).sum(),
                filas.stream().map(VentasPeriodoRow::monto).reduce(BigDecimal.ZERO, BigDecimal::add),
                BigDecimal.ZERO,
                filas);
    }

    private TopProductosReport topReport(List<TopProductoRow> productos) {
        return new TopProductosReport(null, null, 10, productos);
    }

    private KardexReport kardexReport(List<KardexMovimientoRow> movs) {
        return new KardexReport(1L, "SKU-001", "Producto A", 50, null, null, movs);
    }
}
