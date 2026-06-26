package pe.com.krypton.event;

import java.math.BigDecimal;

/**
 * Evento "se creó una orden", consumido desde RabbitMQ.
 *
 * <p>IMPORTANTE: el FQN (pe.com.krypton.event.OrderCreatedEvent) es IDÉNTICO al del productor
 * (order-service). El converter JSON usa el header __TypeId__ con ese nombre para deserializar.
 */
public record OrderCreatedEvent(Long orderId, String userEmail, BigDecimal total) {
}
