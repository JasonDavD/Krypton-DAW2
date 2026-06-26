package pe.com.krypton;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Servicio de Promociones (:8087, base krypton_promos). Codigos de descuento (cupones).
 *
 * <p>{@code @EnableDiscoveryClient}: se registra en Eureka. Autocontenido — no llama a
 * otros servicios (sin Feign): valida y aplica el cupon sobre el monto que le mandan.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class PromoServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromoServiceApplication.class, args);
    }
}
