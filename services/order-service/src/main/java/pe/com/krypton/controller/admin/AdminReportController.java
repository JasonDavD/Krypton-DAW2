package pe.com.krypton.controller.admin;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;
import pe.com.krypton.service.ReportService;

/**
 * Reportes admin. /api/admin/** -> ROLE_ADMIN (SecurityConfig).
 * order-service es la fachada de reportes; el kardex (fase 2) lo trae de catalog por Feign.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

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
}
