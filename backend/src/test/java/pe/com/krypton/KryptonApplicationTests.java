package pe.com.krypton;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.com.krypton.repository.CartItemRepository;
import pe.com.krypton.repository.CartRepository;
import pe.com.krypton.repository.CategoryRepository;
import pe.com.krypton.repository.OrderItemRepository;
import pe.com.krypton.repository.OrderRepository;
import pe.com.krypton.repository.ProductRepository;
import pe.com.krypton.repository.StockMovementRepository;
import pe.com.krypton.repository.UserRepository;

class KryptonApplicationTests extends AbstractIntegrationTest {

    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired StockMovementRepository stockMovements;

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
