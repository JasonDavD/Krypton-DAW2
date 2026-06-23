package pe.com.krypton.controller;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
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
import pe.com.krypton.report.ExcelExporter;
import pe.com.krypton.report.PdfExporter;
import pe.com.krypton.service.ReportService;

/**
 * Admin report export endpoints — 8 endpoints, path-per-format.
 * Authorization: /api/admin/** → hasRole("ADMIN") enforced by SecurityConfig.
 * Controller NEVER touches repositories; delegates to ReportService for data
 * and ExcelExporter/PdfExporter for binary rendering.
 * Satisfies REQ-RPT-01..REQ-RPT-06.
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType PDF  = MediaType.APPLICATION_PDF;

    private final ReportService reportService;
    private final ExcelExporter excelExporter;
    private final PdfExporter   pdfExporter;

    // ─── Datos JSON para el dashboard (mismos reportes, sin exportar a archivo) ────

    /** GET /api/admin/reports/ventas → VentasPorPeriodoReport (JSON, para gráficos/KPIs). */
    @GetMapping("/ventas")
    public VentasPorPeriodoReport ventasDatos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "dia") String granularidad) {
        return reportService.ventasPorPeriodo(desde, hasta, granularidad);
    }

    /** GET /api/admin/reports/productos-vendidos → TopProductosReport (JSON). */
    @GetMapping("/productos-vendidos")
    public TopProductosReport topProductosDatos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.topProductos(desde, hasta, limit);
    }

    /** GET /api/admin/reports/kardex → KardexReport (JSON, movimientos de un producto). */
    @GetMapping("/kardex")
    public KardexReport kardexDatos(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportService.kardexProducto(productId, desde, hasta);
    }

    // ─── R1: Ventas por período ──────────────────────────────────────────────────

    /** GET /api/admin/reports/ventas/excel */
    @GetMapping(value = "/ventas/excel",
                produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> ventasExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "dia") String granularidad) {

        var data  = reportService.ventasPorPeriodo(desde, hasta, granularidad);
        var bytes = excelExporter.exportVentas(data);
        String filename = "ventas_" + desde + "_" + hasta + ".xlsx";
        return file(bytes, filename, XLSX);
    }

    /** GET /api/admin/reports/ventas/pdf */
    @GetMapping(value = "/ventas/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> ventasPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "dia") String granularidad) {

        var data  = reportService.ventasPorPeriodo(desde, hasta, granularidad);
        var bytes = pdfExporter.exportVentas(data);
        String filename = "ventas_" + desde + "_" + hasta + ".pdf";
        return file(bytes, filename, PDF);
    }

    // ─── R2: Productos más vendidos ──────────────────────────────────────────────

    /** GET /api/admin/reports/productos-vendidos/excel */
    @GetMapping(value = "/productos-vendidos/excel",
                produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> topProductosExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "10") int limit) {

        var data  = reportService.topProductos(desde, hasta, limit);
        var bytes = excelExporter.exportTopProductos(data);
        String suffix = (desde != null) ? desde + "_" + hasta : "todos";
        String filename = "productos_vendidos_" + suffix + ".xlsx";
        return file(bytes, filename, XLSX);
    }

    /** GET /api/admin/reports/productos-vendidos/pdf */
    @GetMapping(value = "/productos-vendidos/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> topProductosPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "10") int limit) {

        var data  = reportService.topProductos(desde, hasta, limit);
        var bytes = pdfExporter.exportTopProductos(data);
        String suffix = (desde != null) ? desde + "_" + hasta : "todos";
        String filename = "productos_vendidos_" + suffix + ".pdf";
        return file(bytes, filename, PDF);
    }

    // ─── R3: Kardex por producto ─────────────────────────────────────────────────

    /** GET /api/admin/reports/kardex/excel */
    @GetMapping(value = "/kardex/excel",
                produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> kardexExcel(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        var data  = reportService.kardexProducto(productId, desde, hasta);
        var bytes = excelExporter.exportKardex(data);
        String suffix = (desde != null) ? desde + "_" + hasta : "todos";
        String filename = "kardex_" + productId + "_" + suffix + ".xlsx";
        return file(bytes, filename, XLSX);
    }

    /** GET /api/admin/reports/kardex/pdf */
    @GetMapping(value = "/kardex/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> kardexPdf(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        var data  = reportService.kardexProducto(productId, desde, hasta);
        var bytes = pdfExporter.exportKardex(data);
        String suffix = (desde != null) ? desde + "_" + hasta : "todos";
        String filename = "kardex_" + productId + "_" + suffix + ".pdf";
        return file(bytes, filename, PDF);
    }

    // ─── R4: Listado de órdenes ──────────────────────────────────────────────────

    /** GET /api/admin/reports/ordenes/excel */
    @GetMapping(value = "/ordenes/excel",
                produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> ordenesExcel(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long userId) {

        var data  = reportService.listadoOrdenes(status, desde, hasta, userId);
        var bytes = excelExporter.exportOrdenes(data);
        String suffix = (desde != null) ? desde + "_" + hasta : "todos";
        String filename = "ordenes_" + suffix + ".xlsx";
        return file(bytes, filename, XLSX);
    }

    /** GET /api/admin/reports/ordenes/pdf */
    @GetMapping(value = "/ordenes/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> ordenesPdf(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long userId) {

        var data  = reportService.listadoOrdenes(status, desde, hasta, userId);
        var bytes = pdfExporter.exportOrdenes(data);
        String suffix = (desde != null) ? desde + "_" + hasta : "todos";
        String filename = "ordenes_" + suffix + ".pdf";
        return file(bytes, filename, PDF);
    }

    // ─── private helper ──────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> file(byte[] body, String filename, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(body.length);
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
