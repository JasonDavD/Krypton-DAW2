package pe.com.krypton;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Servicio de Resenas (:8086, base krypton_reviews). Estrellas + comentario por producto.
 *
 * <p>{@code @EnableDiscoveryClient}: se registra en Eureka.
 * <p>{@code @EnableFeignClients}: habilita el cliente Feign hacia catalog-service
 * (valida que el producto reseniado exista antes de guardar la resena).
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class ReviewServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}
