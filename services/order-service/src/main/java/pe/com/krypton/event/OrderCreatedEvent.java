package pe.com.krypton.event;

import java.math.BigDecimal;

/**
 * Evento de dominio: se creó una orden. Viaja por RabbitMQ (JSON) hacia quien lo escuche
 * (notification-service y, a futuro, otros). order NO sabe ni espera quién lo consume.
 *
 * <p>El FQN de esta clase debe ser IDÉNTICO en productor y consumidor: el converter JSON
 * pone el tipo en el header __TypeId__ y el consumidor lo usa para deserializar.
 */
public record OrderCreatedEvent(Long orderId, String userEmail, BigDecimal total) {
}
