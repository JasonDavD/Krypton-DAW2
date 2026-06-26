package pe.com.krypton;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Servicio de Ordenes (:8083, base krypton_orders). Carrito + pedidos + checkout.
 *
 * <p>{@code @EnableDiscoveryClient}: se registra en Eureka.
 * <p>{@code @EnableFeignClients}: habilita los clientes Feign (llama a catalog-service
 * para traer precios y descontar/restaurar stock en el checkout distribuido).
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
