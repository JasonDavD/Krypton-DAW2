package pe.com.krypton.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mensajería (lado CONSUMIDOR). Declara la topología que ESTE servicio necesita:
 * el exchange (compartido), su PROPIA cola, y el binding (qué routing keys quiere recibir).
 *
 * <p>Al arrancar, el RabbitAdmin autoconfigurado crea exchange + cola + binding en RabbitMQ
 * si no existen. Otro servicio podría bindear OTRA cola al mismo exchange sin que order se entere.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "krypton.events";
    public static final String QUEUE = "order.notifications";
    public static final String ROUTING_KEY = "order.created";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue notificationsQueue() {
        return new Queue(QUEUE, true); // durable: sobrevive reinicios del broker
    }

    @Bean
    public Binding orderCreatedBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with(ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
