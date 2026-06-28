package pe.com.krypton.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fila del reporte de ventas: una entrada por bucket de período (día o mes), zona Lima. */
public record VentasPeriodoRow(
        LocalDate periodo,
        long ordenes,
        BigDecimal monto) {
}
