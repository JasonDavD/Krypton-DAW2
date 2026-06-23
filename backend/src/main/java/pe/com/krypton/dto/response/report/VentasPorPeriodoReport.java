package pe.com.krypton.dto.response.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Reporte R1: ventas por período (día o mes), zona horaria America/Lima. */
public record VentasPorPeriodoReport(
        Instant desde,
        Instant hasta,
        String granularidad,
        long totalOrdenes,
        BigDecimal totalFacturado,
        BigDecimal ticketPromedio,
        List<VentasPeriodoRow> filas) {
}
