package pe.com.krypton.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.Usuario;

public interface CarritoRepository extends JpaRepository<Carrito, Long> {

    Optional<Carrito> findByUser(Usuario user);
}
