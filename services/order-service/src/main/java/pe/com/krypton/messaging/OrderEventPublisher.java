package pe.com.krypton.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import pe.com.krypton.config.RabbitConfig;
import pe.com.krypton.event.OrderCreatedEvent;

/** Publica eventos de orden al exchange de RabbitMQ. Fire-and-forget: publica y sigue. */
@Component
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_ORDER_CREATED, event);
    }
}
