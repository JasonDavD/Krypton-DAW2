package pe.com.krypton.dto.response.report;

import java.time.Instant;
import java.util.List;
import pe.com.krypton.dto.response.OrderResponse;

/** Reporte R4: listado de órdenes con filtros opcionales. Reusa {@link OrderResponse}. */
public record OrdenesListadoReport(
        String statusFiltro,
        Instant desde,
        Instant hasta,
        Long userId,
        long total,
        List<OrderResponse> ordenes) {
}
