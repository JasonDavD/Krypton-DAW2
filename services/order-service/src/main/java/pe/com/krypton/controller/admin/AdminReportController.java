package pe.com.krypton.controller.admin;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;
import pe.com.krypton.report.ReportExcelExporter;
import pe.com.krypton.report.ReportPdfExporter;
import pe.com.krypton.service.ReportService;

/**
 * Reportes admin. /api/admin/** -> ROLE_ADMIN (SecurityConfig).
 * order-service es la fachada de reportes; el kardex lo trae de catalog por Feign.
 * El controller NUNCA toca repos: pide los datos al ReportService y delega el render
 * binario a los exporters (Excel/PDF).
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType PDF = MediaType.APPLICATION_PDF;

    private final ReportService reportService;
    private final ReportExcelExporter excelExporter;
    private final ReportPdfExporter pdfExporter;

    public AdminReportController(ReportService reportService,
                                 ReportExcelExporter excelExporter,
                                 ReportPdfExporter pdfExporter) {
        this.reportService = reportService;
        this.excelExporter = excelExporter;
        this.pdfExporter = pdfExporter;
    }

    // ─── Datos JSON (dashboard) ────────────────────────────────────────────────────

    /** GET /api/admin/reports/ventas?desde&hasta&granularidad(dia|mes). */
    @GetMapping("/ventas")
    public VentasPorPeriodoReport ventas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "dia") String granularidad) {
        return reportService.ventasPorPeriodo(desde, hasta, granularidad);
    }

    /** GET /api/admin/reports/productos-vendidos?desde&hasta&limit. */
    @GetMapping("/productos-vendidos")
    public TopProductosReport productosVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.topProductos(desde, hasta, limit);
    }

    /** GET /api/admin/reports/kardex?productId&desde&hasta (el kardex lo trae de catalog por Feign). */
    @GetMapping("/kardex")
    public KardexReport kardex(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportService.kardexProducto(productId, desde, hasta);
    }

    // ─── Exports Excel / PDF ───────────────────────────────────────────────────────

    @GetMapping(value = "/ventas/excel",
                produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> ventasExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "dia") String granularidad) {
        var data = reportService.ventasPorPeriodo(desde, hasta, granularidad);
        return file(excelExporter.exportVentas(data), "ventas_" + desde + "_" + hasta + ".xlsx", XLSX);
    }

    @GetMapping(value = "/ventas/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> ventasPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "dia") String granularidad) {
        var data = reportService.ventasPorPeriodo(desde, hasta, granularidad);
        return file(pdfExporter.exportVentas(data), "ventas_" + desde + "_" + hasta + ".pdf", PDF);
    }

    @GetMapping(value = "/productos-vendidos/excel",
                produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> topProductosExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "10") int limit) {
        var data = reportService.topProductos(desde, hasta, limit);
        String suffix = (desde != null) ? desde + "_" + hasta : "todos";
        return file(excelExporter.exportTopProductos(data), "productos_vendidos_" + suffix + ".xlsx", XLSX);
    }

    @GetMapping(value = "/productos-vendidos/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> topProductosPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "10") int limit) {
        var data = reportService.topProductos(desde, hasta, limit);
        String suffix = (desde != null) ? desde + "_" + hasta : "todos";
        return file(pdfExporter.exportTopProductos(data), "productos_vendidos_" + suffix + ".pdf", PDF);
    }

    @GetMapping(value = "/kardex/excel",
                produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> kardexExcel(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        var data = reportService.kardexProducto(productId, desde, hasta);
        String suffix = (desde != null) ? desde + "_" + hasta : "todos";
        return file(excelExporter.exportKardex(data), "kardex_" + productId + "_" + suffix + ".xlsx", XLSX);
    }

    @GetMapping(value = "/kardex/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> kardexPdf(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        var data = reportService.kardexProducto(productId, desde, hasta);
        String suffix = (desde != null) ? desde + "_" + hasta : "todos";
        return file(pdfExporter.exportKardex(data), "kardex_" + productId + "_" + suffix + ".pdf", PDF);
    }

    // ─── private helper ────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> file(byte[] body, String filename, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
