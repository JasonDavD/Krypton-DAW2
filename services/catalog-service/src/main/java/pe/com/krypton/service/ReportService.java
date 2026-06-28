package pe.com.krypton.service;

import java.time.Instant;
import pe.com.krypton.dto.response.report.KardexReport;

/** Reportes de los que catalog es dueño del dato (kardex = stock_movement). */
public interface ReportService {

    /**
     * Kardex de un producto. Rango [start, end) opcional (ambos o ninguno).
     * 404 (ResourceNotFoundException) si el producto no existe.
     */
    KardexReport kardex(Long productId, Instant start, Instant end);
}
