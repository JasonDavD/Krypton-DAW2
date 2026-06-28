package pe.com.krypton.dto.response.report;

import java.math.BigDecimal;

/** Proyección de la query nativa ventasTotales (alias = nombre de columna). */
public interface VentasTotalesProjection {
    long getTotalOrdenes();
    BigDecimal getTotalFacturado();
    BigDecimal getTicketPromedio();
}
