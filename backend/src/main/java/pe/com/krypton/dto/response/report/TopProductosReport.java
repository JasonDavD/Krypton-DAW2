package pe.com.krypton.dto.response.report;

import java.time.Instant;
import java.util.List;

/** Reporte R2: productos más vendidos (por cantidad), capped a {@code limit}. */
public record TopProductosReport(
        Instant desde,
        Instant hasta,
        int limit,
        List<TopProductoRow> productos) {
}
