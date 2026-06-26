package pe.com.krypton.dto.response.report;

import java.time.Instant;
import java.util.List;
import pe.com.krypton.dto.response.OrdenResponse;

/** Reporte R4: listado de órdenes con filtros opcionales. Reusa {@link OrdenResponse}. */
public record OrdenesListadoReport(
        String statusFiltro,
        Instant desde,
        Instant hasta,
        Long userId,
        long total,
        List<OrdenResponse> ordenes) {
}
