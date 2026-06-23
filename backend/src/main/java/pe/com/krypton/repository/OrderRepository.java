package pe.com.krypton.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.com.krypton.model.Order;
import pe.com.krypton.model.User;

public interface OrderRepository extends JpaRepository<Order, Long>,
        JpaSpecificationExecutor<Order> {

    List<Order> findByUserOrderByOrderDateDesc(User user);

    Optional<Order> findByIdAndUser(Long id, User user);

    // -------------------------------------------------------------------------
    // R1: Ventas por período — agrupadas por bucket Lima (day/month)
    // -------------------------------------------------------------------------

    /**
     * Retorna una fila por bucket de período en zona horaria America/Lima.
     * Solo incluye órdenes con status = 'CONFIRMADA'.
     * {@code :gran} debe ser 'day' o 'month' (pasado como String literal por el servicio).
     * El rango es half-open: order_date >= :start AND order_date < :end.
     */
    @Query(value = """
            SELECT CASE :gran
                     WHEN 'day'   THEN DATE(CONVERT_TZ(o.order_date, '+00:00', '-05:00'))
                     WHEN 'month' THEN DATE(DATE_FORMAT(CONVERT_TZ(o.order_date, '+00:00', '-05:00'), '%Y-%m-01'))
                   END                        AS periodo,
                   COUNT(o.id)                AS ordenes,
                   COALESCE(SUM(o.total), 0)  AS monto
            FROM orders o
            WHERE o.status = 'CONFIRMADA'
              AND o.order_date >= :start
              AND o.order_date < :end
            GROUP BY periodo
            ORDER BY periodo
            """,
            nativeQuery = true)
    List<VentasPeriodoProjection> ventasPorPeriodo(
            @Param("gran") String gran,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Retorna totales aggregados del período: count, sum, avg.
     * Solo incluye órdenes con status = 'CONFIRMADA'.
     * Rango half-open: order_date >= :start AND order_date < :end.
     */
    @Query(value = """
            SELECT COUNT(id)                 AS totalOrdenes,
                   COALESCE(SUM(total), 0)   AS totalFacturado,
                   COALESCE(AVG(total), 0)   AS ticketPromedio
            FROM orders
            WHERE status = 'CONFIRMADA'
              AND order_date >= :start
              AND order_date < :end
            """,
            nativeQuery = true)
    VentasTotalesProjection ventasTotales(
            @Param("start") Instant start,
            @Param("end") Instant end);
}
