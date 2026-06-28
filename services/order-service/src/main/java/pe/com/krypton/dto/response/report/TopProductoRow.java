package pe.com.krypton.dto.response.report;

import java.math.BigDecimal;

/**
 * Fila del reporte de productos más vendidos.
 * ADAPTACION microservicios: el snapshot de order_items NO guarda sku (solo productId +
 * productName), así que sku viaja vacío. {@code unidades} es Long porque SUM(quantity) en
 * Hibernate 6 devuelve Long.
 */
public record TopProductoRow(
        Long productId,
        String sku,
        String nombre,
        Long unidades,
        BigDecimal ingresos) {
}
