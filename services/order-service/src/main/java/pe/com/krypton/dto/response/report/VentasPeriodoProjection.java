package pe.com.krypton.dto.response.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Proyección de la query nativa ventasPorPeriodo (alias = nombre de columna). */
public interface VentasPeriodoProjection {
    LocalDate getPeriodo();
    long getOrdenes();
    BigDecimal getMonto();
}
