package pe.com.krypton.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.krypton.entity.Pago;

public interface PagoRepository extends JpaRepository<Pago, Long> {
}
