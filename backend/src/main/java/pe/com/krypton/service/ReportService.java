package pe.com.krypton.service;

import java.time.LocalDate;
import pe.com.krypton.dto.response.report.KardexReport;
import pe.com.krypton.dto.response.report.OrdenesListadoReport;
import pe.com.krypton.dto.response.report.TopProductosReport;
import pe.com.krypton.dto.response.report.VentasPorPeriodoReport;

/** Servicio de reportes exportables. Solo lectura — sin efectos secundarios. */
public interface ReportService {

    /**
     * R1: Ventas por período agrupadas por día o mes en zona horaria America/Lima.
     *
     * @param desde        fecha de inicio (inclusive) en zona Lima
     * @param hasta        fecha de fin (inclusive) en zona Lima
     * @param granularidad {@code "dia"} o {@code "mes"}; inválido → IllegalArgumentException
     * @throws IllegalArgumentException si desde > hasta, granularidad inválida
     */
    VentasPorPeriodoReport ventasPorPeriodo(LocalDate desde, LocalDate hasta, String granularidad);

    /**
     * R2: Productos más vendidos, ordenados por unidades DESC, capped a {@code limit}.
     *
     * @param desde fecha de inicio opcional; si una es presente la otra también debe estarlo
     * @param hasta fecha de fin opcional
     * @param limit 1..100
     * @throws IllegalArgumentException si limit fuera de rango, rango de fechas parcial, desde > hasta
     */
    TopProductosReport topProductos(LocalDate desde, LocalDate hasta, int limit);

    /**
     * R3: Historial de movimientos (kardex) de un producto.
     *
     * @param productId producto requerido; inexistente → ResourceNotFoundException (404)
     * @param desde     fecha de inicio opcional; si una es presente la otra también debe estarlo
     * @param hasta     fecha de fin opcional
     * @throws pe.com.krypton.exception.ResourceNotFoundException si el producto no existe
     * @throws IllegalArgumentException si rango de fechas parcial
     */
    KardexReport kardexProducto(Long productId, LocalDate desde, LocalDate hasta);

    /**
     * R4: Listado de órdenes con filtros opcionales.
     *
     * @param status  nombre del enum OrderStatus (case-insensitive), o null para sin filtro
     * @param desde   fecha de inicio opcional
     * @param hasta   fecha de fin opcional
     * @param userId  ID del usuario (opcional)
     * @throws IllegalArgumentException si status inválido, rango de fechas parcial
     */
    OrdenesListadoReport listadoOrdenes(String status, LocalDate desde, LocalDate hasta, Long userId);
}
