package pe.com.krypton.dto.response.report;

import java.time.Instant;
import java.util.List;

/** Kardex de un producto (contrato Feign: lo provee catalog-service). */
public record KardexReport(
        Long productId,
        String sku,
        String nombre,
        int stockActual,
        Instant desde,
        Instant hasta,
        List<KardexMovimientoRow> movimientos) {
}
