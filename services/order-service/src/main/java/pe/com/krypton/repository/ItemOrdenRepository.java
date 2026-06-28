package pe.com.krypton.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.com.krypton.entity.ItemOrden;
import pe.com.krypton.entity.Orden;

public interface ItemOrdenRepository extends JpaRepository<ItemOrden, Long> {

    /** Líneas de la orden. */
    List<ItemOrden> findByOrder(Orden order);

    // ── Reportes admin: productos más vendidos ──
    // Devuelve Object[] = {productId(Long), productName(String), unidades(Number), ingresos(BigDecimal)}.
    // En MySQL SUM(int) puede volver BigDecimal: el mapeo a long lo hace el service.

    /** Top productos por unidades en [start, end), solo órdenes CONFIRMADA. */
    @Query("""
            SELECT oi.productId, oi.productName,
                   SUM(oi.quantity), SUM(oi.quantity * oi.unitPrice)
            FROM ItemOrden oi
            JOIN oi.order o
            WHERE o.status IN (pe.com.krypton.entity.enums.EstadoOrden.CONFIRMADA,
                               pe.com.krypton.entity.enums.EstadoOrden.ENVIADO,
                               pe.com.krypton.entity.enums.EstadoOrden.ENTREGADO)
              AND o.orderDate >= :start
              AND o.orderDate < :end
            GROUP BY oi.productId, oi.productName
            ORDER BY SUM(oi.quantity) DESC
            """)
    List<Object[]> findTopProductos(
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable);

    /** Variante sin rango de fechas — todas las órdenes CONFIRMADA. */
    @Query("""
            SELECT oi.productId, oi.productName,
                   SUM(oi.quantity), SUM(oi.quantity * oi.unitPrice)
            FROM ItemOrden oi
            JOIN oi.order o
            WHERE o.status IN (pe.com.krypton.entity.enums.EstadoOrden.CONFIRMADA,
                               pe.com.krypton.entity.enums.EstadoOrden.ENVIADO,
                               pe.com.krypton.entity.enums.EstadoOrden.ENTREGADO)
            GROUP BY oi.productId, oi.productName
            ORDER BY SUM(oi.quantity) DESC
            """)
    List<Object[]> findTopProductosSinFechas(Pageable pageable);
}
