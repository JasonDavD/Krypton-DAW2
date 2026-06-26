package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.com.krypton.repository.ItemCarritoRepository;
import pe.com.krypton.repository.CarritoRepository;
import pe.com.krypton.repository.CategoriaRepository;
import pe.com.krypton.repository.ItemOrdenRepository;
import pe.com.krypton.repository.OrdenRepository;
import pe.com.krypton.repository.ProductoRepository;
import pe.com.krypton.repository.MovimientoStockRepository;
import pe.com.krypton.repository.UsuarioRepository;

class KryptonApplicationTests extends AbstractIntegrationTest {

    @Autowired UsuarioRepository users;
    @Autowired CategoriaRepository categories;
    @Autowired ProductoRepository products;
    @Autowired CarritoRepository carts;
    @Autowired ItemCarritoRepository cartItems;
    @Autowired OrdenRepository orders;
    @Autowired ItemOrdenRepository orderItems;
    @Autowired MovimientoStockRepository stockMovements;

    @Test
    void contextLoads() {
        // Si el contexto arranca, ddl-auto: validate confirmó que las 8 entidades
        // coinciden EXACTAMENTE con el schema creado por Flyway V1.
    }

    @Test
    void all_entities_map_to_their_tables() {
        // Cada count() ejecuta un SELECT sobre la tabla mapeada:
        // prueba el binding entidad <-> tabla de las 8 entidades.
        // No vacías por seed de Flyway: V3 siembra el ADMIN; V6 siembra categorías + productos demo.
        assertThat(users.count()).isPositive();
        assertThat(categories.count()).isPositive();
        assertThat(products.count()).isPositive();
        assertThat(carts.count()).isZero();
        assertThat(cartItems.count()).isZero();
        assertThat(orders.count()).isZero();
        assertThat(orderItems.count()).isZero();
        assertThat(stockMovements.count()).isZero();
    }
}
