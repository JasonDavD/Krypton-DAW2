package pe.com.krypton.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.ItemCarrito;
import pe.com.krypton.entity.Producto;

public interface ItemCarritoRepository extends JpaRepository<ItemCarrito, Long> {

    Optional<ItemCarrito> findByCartAndProduct(Carrito cart, Producto product);

    List<ItemCarrito> findByCart(Carrito cart);

    void deleteByCart(Carrito cart);
}
