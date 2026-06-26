package pe.com.krypton;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Servicio de Pagos (:8085, base krypton_payments). Procesa cobros del checkout.
 *
 * <p>{@code @EnableDiscoveryClient}: se registra en Eureka. Lo descubre y lo llama
 * order-service via Feign (POST /api/payments/charge) durante la confirmacion de compra.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
