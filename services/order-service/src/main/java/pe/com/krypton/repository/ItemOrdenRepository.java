package pe.com.krypton.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.krypton.entity.ItemOrden;
import pe.com.krypton.entity.Orden;

public interface ItemOrdenRepository extends JpaRepository<ItemOrden, Long> {

    /** Líneas de la orden. */
    List<ItemOrden> findByOrder(Orden order);
}
