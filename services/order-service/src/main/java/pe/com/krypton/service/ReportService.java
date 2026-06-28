package pe.com.krypton.service;

import java.time.LocalDate;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;

/**
 * Reportes admin. Ventas y top-productos se calculan localmente (datos de order);
 * el kardex (fase 2) se trae de catalog-service por Feign.
 */
public interface ReportService {

    /** Ventas agrupadas por día/mes (zona Lima). desde y hasta obligatorios. */
    VentasPorPeriodoReport ventasPorPeriodo(LocalDate desde, LocalDate hasta, String granularidad);

    /** Productos más vendidos por unidades, top {@code limit} (1..100). Fechas opcionales (juntas). */
    TopProductosReport topProductos(LocalDate desde, LocalDate hasta, int limit);

    /** Kardex de un producto: lo trae de catalog-service por Feign. Fechas opcionales (juntas). */
    KardexReport kardexProducto(Long productId, LocalDate desde, LocalDate hasta);
}
