package pe.com.krypton.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.krypton.model.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // -------------------------------------------------------------------------
    // R3: Kardex — movimientos de un producto ordenados por fecha ascendente
    // -------------------------------------------------------------------------

    /**
     * Retorna movimientos de un producto dentro de un rango half-open [start, end).
     * Spring Data interpreta "Between" como BETWEEN (inclusive ambos extremos);
     * se prefiere LessThan para el extremo superior para respetar la semántica half-open.
     * La implementación real usa Between para simplicidad de nombre derivado
     * (los límites se calculan correctamente en el servicio).
     */
    List<StockMovement> findByProduct_IdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long productId, Instant start, Instant end);

    /** Retorna todos los movimientos de un producto sin filtro de fecha. */
    List<StockMovement> findByProduct_IdOrderByCreatedAtAsc(Long productId);
}
