package pe.com.krypton.dto.response.report;

import java.math.BigDecimal;

/**
 * Fila del reporte R2: producto más vendido.
 * NOTE: {@code unidades} es {@code Long} (boxeado) porque Hibernate 6.x devuelve
 * {@code Long} para {@code SUM(oi.quantity)} en JPQL constructor-expression.
 * Verificado empíricamente en {@code ReportRepositoryIntegrationTest}.
 */
public record TopProductoRow(
        Long productId,
        String sku,
        String nombre,
        Long unidades,
        BigDecimal ingresos) {
}
