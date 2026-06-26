package pe.com.krypton;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Portón único de entrada. Escucha en :8080 (donde apunta el frontend) y rutea
 * cada request al microservicio correspondiente, resolviéndolo por nombre vía
 * Eureka (uri lb://nombre-servicio). Centraliza también el CORS.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
