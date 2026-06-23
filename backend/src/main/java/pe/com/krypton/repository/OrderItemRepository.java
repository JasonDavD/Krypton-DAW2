package pe.com.krypton.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.com.krypton.dto.response.report.TopProductoRow;
import pe.com.krypton.model.Order;
import pe.com.krypton.model.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder(Order order);

    // -------------------------------------------------------------------------
    // R2: Productos más vendidos — JPQL constructor-expression → TopProductoRow
    // -------------------------------------------------------------------------

    /**
     * Retorna los productos más vendidos (por unidades) en el período indicado.
     * Solo incluye ítems de órdenes CONFIRMADAS.
     * El rango es half-open: o.orderDate >= :start AND o.orderDate < :end.
     * Usa {@link Pageable} para limitar a top-N (PageRequest.of(0, limit)).
     *
     * NOTE (design risk R2): SUM(oi.quantity) → Long, SUM(oi.quantity * oi.unitPrice) → BigDecimal
     * en Hibernate 6.x con Postgres. El test de integración lo verifica empíricamente.
     */
    @Query("""
            SELECT new pe.com.krypton.dto.response.report.TopProductoRow(
                       oi.product.id,
                       oi.product.sku,
                       oi.product.name,
                       SUM(oi.quantity),
                       SUM(oi.quantity * oi.unitPrice))
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.status = pe.com.krypton.model.enums.OrderStatus.CONFIRMADA
              AND o.orderDate >= :start
              AND o.orderDate < :end
            GROUP BY oi.product.id, oi.product.sku, oi.product.name
            ORDER BY SUM(oi.quantity) DESC
            """)
    List<TopProductoRow> findTopProductos(
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable);

    /**
     * Variante sin rango de fechas — incluye todas las órdenes CONFIRMADAS.
     */
    @Query("""
            SELECT new pe.com.krypton.dto.response.report.TopProductoRow(
                       oi.product.id,
                       oi.product.sku,
                       oi.product.name,
                       SUM(oi.quantity),
                       SUM(oi.quantity * oi.unitPrice))
            FROM OrderItem oi
            JOIN oi.order o
            WHERE o.status = pe.com.krypton.model.enums.OrderStatus.CONFIRMADA
            GROUP BY oi.product.id, oi.product.sku, oi.product.name
            ORDER BY SUM(oi.quantity) DESC
            """)
    List<TopProductoRow> findTopProductosSinFechas(Pageable pageable);
}
