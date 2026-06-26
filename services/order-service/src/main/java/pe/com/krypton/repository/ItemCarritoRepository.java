package pe.com.krypton.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.krypton.entity.Carrito;
import pe.com.krypton.entity.ItemCarrito;

public interface ItemCarritoRepository extends JpaRepository<ItemCarrito, Long> {

    /** Líneas del carrito. */
    List<ItemCarrito> findByCart(Carrito cart);

    /** Línea de un producto puntual dentro del carrito (UNIQUE cart_id + product_id). */
    Optional<ItemCarrito> findByCartAndProductId(Carrito cart, Long productId);

    /** Vacía el carrito borrando todas sus líneas. */
    void deleteByCart(Carrito cart);
}
