package pe.com.krypton.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.krypton.entity.Orden;

public interface OrdenRepository extends JpaRepository<Orden, Long> {

    /** Órdenes del usuario, más recientes primero. */
    List<Orden> findByUserEmailOrderByOrderDateDesc(String userEmail);

    /** Una orden propia (filtra por dueño para evitar IDOR). */
    Optional<Orden> findByIdAndUserEmail(Long id, String userEmail);
}
