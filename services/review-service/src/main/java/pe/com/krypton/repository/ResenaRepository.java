package pe.com.krypton.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.com.krypton.entity.Resena;

public interface ResenaRepository extends JpaRepository<Resena, Long> {

    /** Resenas de un producto, mas recientes primero. */
    List<Resena> findByProductIdOrderByCreatedAtDesc(Long productId);

    /** ¿El usuario ya reseno este producto? (respalda el UNIQUE de la tabla). */
    boolean existsByUserEmailAndProductId(String userEmail, Long productId);

    /** Cantidad de resenas de un producto. */
    long countByProductId(Long productId);

    /** Promedio de estrellas de un producto (null si no hay resenas). */
    @Query("SELECT AVG(r.rating) FROM Resena r WHERE r.productId = :productId")
    Double promedioByProductId(@Param("productId") Long productId);
}
