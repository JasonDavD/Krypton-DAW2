package pe.com.krypton.dto.response.report;

import java.time.Instant;
import java.util.List;

/** Reporte R3: kardex (historial de movimientos) de un producto. */
public record KardexReport(
        Long productId,
        String sku,
        String nombre,
        int stockActual,
        Instant desde,
        Instant hasta,
        List<KardexMovimientoRow> movimientos) {
}
