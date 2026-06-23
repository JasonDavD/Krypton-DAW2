package pe.com.krypton.repository;

import java.math.BigDecimal;

/**
 * Proyección de interfaz para la consulta nativa R1 {@code ventasTotales}.
 * Retorna agregados totales del período: count, sum, avg.
 */
public interface VentasTotalesProjection {

    long getTotalOrdenes();

    BigDecimal getTotalFacturado();

    BigDecimal getTicketPromedio();
}
