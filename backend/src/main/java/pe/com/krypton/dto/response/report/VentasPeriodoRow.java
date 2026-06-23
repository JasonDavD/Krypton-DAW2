package pe.com.krypton.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fila del reporte R1: una entrada por bucket de período (día o mes) en zona Lima. */
public record VentasPeriodoRow(
        LocalDate periodo,
        long ordenes,
        BigDecimal monto) {
}
