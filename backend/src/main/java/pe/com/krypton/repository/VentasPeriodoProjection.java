package pe.com.krypton.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Proyección de interfaz para la consulta nativa R1 {@code ventasPorPeriodo}.
 * Spring Data mapea cada columna del ResultSet a su getter correspondiente.
 */
public interface VentasPeriodoProjection {

    LocalDate getPeriodo();

    long getOrdenes();

    BigDecimal getMonto();
}
