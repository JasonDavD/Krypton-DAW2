package pe.com.krypton.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pe.com.krypton.config.RabbitConfig;
import pe.com.krypton.event.OrderCreatedEvent;

/**
 * Consume los eventos OrderCreated de la cola. Acá "se envía" la notificación al cliente
 * (en el demo lo logueamos; en prod sería un email/SMS/push). order NO espera esto: es asíncrono.
 */
@Component
public class OrderNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationListener.class);

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("=== [notification] Orden #{} de {} por total S/ {} -> enviando confirmacion al cliente ===",
                event.orderId(), event.userEmail(), event.total());
    }
}
