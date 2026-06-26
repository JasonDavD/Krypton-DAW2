package pe.com.krypton;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Servicio de Notificaciones (:8084). Consumidor PURO de eventos de RabbitMQ: no tiene DB
 * ni endpoints de negocio. Demuestra la mensajería ASÍNCRONA (desacoplada de order).
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
