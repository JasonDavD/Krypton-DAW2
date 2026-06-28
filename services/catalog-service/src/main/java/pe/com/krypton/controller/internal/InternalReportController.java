package pe.com.krypton.controller.internal;

import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.service.ReportService;

/**
 * Endpoint INTERNO de reportes (servicio-a-servicio, para Feign desde order-service).
 * NO se rutea por el api-gateway; exige JWT autenticado (order reenvía el token del admin).
 */
@RestController
@RequestMapping("/api/internal/reports")
public class InternalReportController {

    private final ReportService reportService;

    public InternalReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** GET /api/internal/reports/kardex?productId&start&end (Instant ISO, opcionales). */
    @GetMapping("/kardex")
    public KardexReport kardex(
            @RequestParam Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return reportService.kardex(productId, start, end);
    }
}
