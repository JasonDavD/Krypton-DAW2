package pe.com.krypton.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pe.com.krypton.dto.response.report.KardexMovimientoRow;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.OrdenesListadoReport;
import pe.com.krypton.dto.response.report.TopProductoRow;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPeriodoRow;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;
import pe.com.krypton.report.ExcelExporter;
import pe.com.krypton.report.PdfExporter;
import pe.com.krypton.security.JwtAuthenticationFilter;
import pe.com.krypton.service.ReportService;

/**
 * Web slice for AdminReportController.
 * SecurityContext disabled via addFilters=false + JwtAuthenticationFilter exclusion.
 * ReportService, ExcelExporter, PdfExporter all mocked.
 * Role assigned via @WithMockUser(roles = "ADMIN").
 * Covers HTTP contract: status codes, Content-Type, Content-Disposition, 400 validation.
 * Satisfies REQ-RPT-05, REQ-RPT-06, REQ-RPT-07.
 */
@WebMvcTest(controllers = AdminReportController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class AdminReportControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ReportService reportService;
    @MockBean ExcelExporter excelExporter;
    @MockBean PdfExporter pdfExporter;

    private static final String XLSX_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_TYPE = "application/pdf";

    // Minimal fake bytes — just enough to be non-null and non-empty
    private static final byte[] FAKE_XLSX = new byte[]{0x50, 0x4B, 0x03, 0x04}; // PK magic
    private static final byte[] FAKE_PDF  = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF

    // ─── stub helpers ────────────────────────────────────────────────────────────

    private VentasPorPeriodoReport stubVentasReport() {
        return new VentasPorPeriodoReport(
                Instant.now(), Instant.now(), "dia", 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    private TopProductosReport stubTopProductosReport() {
        return new TopProductosReport(null, null, 10, List.of());
    }

    private KardexReport stubKardexReport() {
        return new KardexReport(1L, "SKU-001", "Test Product", 5, null, null, List.of());
    }

    private OrdenesListadoReport stubOrdenesReport() {
        return new OrdenesListadoReport(null, null, null, null, 0L, List.of());
    }

    // ─── GET /ventas y /productos-vendidos (JSON para el dashboard) ───────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void ventas_datos_returns_200_json() throws Exception {
        when(reportService.ventasPorPeriodo(any(), any(), any())).thenReturn(
                new VentasPorPeriodoReport(Instant.now(), Instant.now(), "dia",
                        12L, new BigDecimal("4500.00"), new BigDecimal("375.00"),
                        List.of(new VentasPeriodoRow(LocalDate.of(2024, 1, 1), 3L, new BigDecimal("900.00")))));

        mvc.perform(get("/api/admin/reports/ventas")
                        .param("desde", "2024-01-01").param("hasta", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.totalOrdenes").value(12))
                .andExpect(jsonPath("$.totalFacturado").value(4500.00))
                .andExpect(jsonPath("$.filas.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ventas_datos_missing_desde_returns_400() throws Exception {
        mvc.perform(get("/api/admin/reports/ventas").param("hasta", "2024-01-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void top_productos_datos_returns_200_json() throws Exception {
        when(reportService.topProductos(any(), any(), eq(10))).thenReturn(
                new TopProductosReport(null, null, 10,
                        List.of(new TopProductoRow(1L, "SKU-1", "Laptop", 9L, new BigDecimal("8100.00")))));

        mvc.perform(get("/api/admin/reports/productos-vendidos").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos[0].nombre").value("Laptop"))
                .andExpect(jsonPath("$.productos[0].unidades").value(9));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void kardex_datos_returns_200_json() throws Exception {
        when(reportService.kardexProducto(eq(1L), isNull(), isNull())).thenReturn(
                new KardexReport(1L, "SKU-001", "Laptop", 8, Instant.now(), Instant.now(),
                        List.of(new KardexMovimientoRow(Instant.now(), "SALIDA", 2, "Venta", "ORDER-5"))));

        mvc.perform(get("/api/admin/reports/kardex").param("productId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Laptop"))
                .andExpect(jsonPath("$.stockActual").value(8))
                .andExpect(jsonPath("$.movimientos[0].tipo").value("SALIDA"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void kardex_datos_missing_productId_returns_400() throws Exception {
        mvc.perform(get("/api/admin/reports/kardex"))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /ventas/excel ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void ventas_excel_returns_200_xlsx_with_disposition() throws Exception {
        when(reportService.ventasPorPeriodo(any(), any(), any())).thenReturn(stubVentasReport());
        when(excelExporter.exportVentas(any())).thenReturn(FAKE_XLSX);

        mvc.perform(get("/api/admin/reports/ventas/excel")
                        .param("desde", "2024-01-01")
                        .param("hasta", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX_TYPE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".xlsx")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ventas_excel_missing_desde_returns_400() throws Exception {
        mvc.perform(get("/api/admin/reports/ventas/excel")
                        .param("hasta", "2024-01-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ventas_excel_missing_hasta_returns_400() throws Exception {
        mvc.perform(get("/api/admin/reports/ventas/excel")
                        .param("desde", "2024-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ventas_excel_default_granularidad_is_dia() throws Exception {
        when(reportService.ventasPorPeriodo(any(), any(), eq("dia"))).thenReturn(stubVentasReport());
        when(excelExporter.exportVentas(any())).thenReturn(FAKE_XLSX);

        mvc.perform(get("/api/admin/reports/ventas/excel")
                        .param("desde", "2024-01-01")
                        .param("hasta", "2024-01-31"))
                // No granularidad param → should use default "dia"
                .andExpect(status().isOk());
    }

    // ─── GET /ventas/pdf ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void ventas_pdf_returns_200_pdf_with_disposition() throws Exception {
        when(reportService.ventasPorPeriodo(any(), any(), any())).thenReturn(stubVentasReport());
        when(pdfExporter.exportVentas(any())).thenReturn(FAKE_PDF);

        mvc.perform(get("/api/admin/reports/ventas/pdf")
                        .param("desde", "2024-01-01")
                        .param("hasta", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(PDF_TYPE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".pdf")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ventas_pdf_missing_desde_returns_400() throws Exception {
        mvc.perform(get("/api/admin/reports/ventas/pdf")
                        .param("hasta", "2024-01-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    // ─── GET /productos-vendidos/excel ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void top_productos_excel_returns_200_xlsx() throws Exception {
        when(reportService.topProductos(isNull(), isNull(), eq(10))).thenReturn(stubTopProductosReport());
        when(excelExporter.exportTopProductos(any())).thenReturn(FAKE_XLSX);

        mvc.perform(get("/api/admin/reports/productos-vendidos/excel"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX_TYPE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void top_productos_excel_default_limit_is_10() throws Exception {
        when(reportService.topProductos(isNull(), isNull(), eq(10))).thenReturn(stubTopProductosReport());
        when(excelExporter.exportTopProductos(any())).thenReturn(FAKE_XLSX);

        mvc.perform(get("/api/admin/reports/productos-vendidos/excel"))
                // No limit param → default 10 → service called with limit=10
                .andExpect(status().isOk());
    }

    // ─── GET /productos-vendidos/pdf ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void top_productos_pdf_returns_200_pdf() throws Exception {
        when(reportService.topProductos(isNull(), isNull(), eq(10))).thenReturn(stubTopProductosReport());
        when(pdfExporter.exportTopProductos(any())).thenReturn(FAKE_PDF);

        mvc.perform(get("/api/admin/reports/productos-vendidos/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(PDF_TYPE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    // ─── GET /kardex/excel ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void kardex_excel_returns_200_xlsx() throws Exception {
        when(reportService.kardexProducto(eq(1L), isNull(), isNull())).thenReturn(stubKardexReport());
        when(excelExporter.exportKardex(any())).thenReturn(FAKE_XLSX);

        mvc.perform(get("/api/admin/reports/kardex/excel")
                        .param("productId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX_TYPE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void kardex_excel_missing_productId_returns_400() throws Exception {
        mvc.perform(get("/api/admin/reports/kardex/excel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    // ─── GET /kardex/pdf ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void kardex_pdf_returns_200_pdf() throws Exception {
        when(reportService.kardexProducto(eq(1L), isNull(), isNull())).thenReturn(stubKardexReport());
        when(pdfExporter.exportKardex(any())).thenReturn(FAKE_PDF);

        mvc.perform(get("/api/admin/reports/kardex/pdf")
                        .param("productId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(PDF_TYPE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void kardex_pdf_missing_productId_returns_400() throws Exception {
        mvc.perform(get("/api/admin/reports/kardex/pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    // ─── GET /ordenes/excel ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void ordenes_excel_no_params_returns_200_xlsx() throws Exception {
        when(reportService.listadoOrdenes(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(stubOrdenesReport());
        when(excelExporter.exportOrdenes(any())).thenReturn(FAKE_XLSX);

        mvc.perform(get("/api/admin/reports/ordenes/excel"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX_TYPE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ordenes_excel_invalid_status_returns_400() throws Exception {
        when(reportService.listadoOrdenes(eq("INVALIDO"), isNull(), isNull(), isNull()))
                .thenThrow(new IllegalArgumentException("Estado inválido: INVALIDO"));

        mvc.perform(get("/api/admin/reports/ordenes/excel")
                        .param("status", "INVALIDO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    // ─── GET /ordenes/pdf ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void ordenes_pdf_no_params_returns_200_pdf() throws Exception {
        when(reportService.listadoOrdenes(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(stubOrdenesReport());
        when(pdfExporter.exportOrdenes(any())).thenReturn(FAKE_PDF);

        mvc.perform(get("/api/admin/reports/ordenes/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(PDF_TYPE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    // ─── Auth notes ──────────────────────────────────────────────────────────────
    // 401 (no token) and 403 (CLIENTE role) are verified in ReportIntegrationTest (P7).
    // addFilters=false disables the full security filter chain (including ExceptionTranslationFilter
    // and FilterSecurityInterceptor), so role-based 403 cannot be reliably tested here.
    // This mirrors the same W-01 trade-off documented in AdminOrderControllerTest.
}
