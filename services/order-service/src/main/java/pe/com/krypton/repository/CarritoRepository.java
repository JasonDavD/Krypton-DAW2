package pe.com.krypton.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.krypton.entity.Carrito;

public interface CarritoRepository extends JpaRepository<Carrito, Long> {

    /** Carrito del usuario (identidad = email del JWT). */
    Optional<Carrito> findByUserEmail(String userEmail);
}
