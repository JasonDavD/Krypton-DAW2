package pe.com.krypton.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mensajería (lado PRODUCTOR). Declara el exchange y el converter JSON.
 *
 * <p>Publicamos a un TOPIC exchange con routing key "order.created". order solo conoce el
 * exchange — NO sabe qué colas ni qué consumidores hay detrás (desacople total). El Jackson
 * converter serializa el evento a JSON; el RabbitTemplate autoconfigurado lo toma solo.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "krypton.events";
    public static final String ROUTING_ORDER_CREATED = "order.created";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
