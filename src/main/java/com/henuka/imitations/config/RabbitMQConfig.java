package com.henuka.imitations.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.host}")
    private String host;

    @Value("${spring.rabbitmq.port}")
    private int port;

    @Value("${spring.rabbitmq.username}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

    // Queue names
    public static final String ORDER_QUEUE = "order-queue";
    public static final String NOTIFICATION_QUEUE = "notification-queue";
    public static final String EMAIL_QUEUE = "email-queue";

    // Exchange names
    public static final String ORDER_EXCHANGE = "order-exchange";
    public static final String NOTIFICATION_EXCHANGE = "notification-exchange";

    // Routing keys
    public static final String ORDER_ROUTING_KEY = "order.#";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.#";

    /**
     * Configure connection factory
     */
    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        return factory;
    }

    /**
     * Configure RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        template.setConfirmCallback((correlation, ack, reason) -> {
            if (!ack) {
                log.error("Message delivery failed: {}", reason);
            }
        });
        return template;
    }

    /**
     * Configure queues
     */
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", "dlx")
                .withArgument("x-dead-letter-routing-key", "dlq")
                .build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", "dlx")
                .withArgument("x-dead-letter-routing-key", "dlq")
                .build();
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", "dlx")
                .withArgument("x-dead-letter-routing-key", "dlq")
                .build();
    }

    /**
     * Configure exchanges
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    /**
     * Configure bindings
     */
    @Bean
    public Binding orderBinding() {
        return BindingBuilder
                .bind(orderQueue())
                .to(orderExchange())
                .with(ORDER_ROUTING_KEY);
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(notificationExchange())
                .with(NOTIFICATION_ROUTING_KEY);
    }
}

/**
 * Message service
 */
@org.springframework.stereotype.Service
class MessageService {
    
    private final RabbitTemplate rabbitTemplate;
    private final MessageMetrics messageMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MessageService.class);

    public MessageService(RabbitTemplate rabbitTemplate,
                         MessageMetrics messageMetrics) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageMetrics = messageMetrics;
    }

    /**
     * Send message
     */
    public void sendMessage(String exchange, String routingKey, Object message) {
        try {
            long startTime = System.currentTimeMillis();
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            long duration = System.currentTimeMillis() - startTime;
            
            messageMetrics.recordMessageSent(exchange, routingKey, duration);
            log.debug("Message sent to exchange: {} with routing key: {}", exchange, routingKey);
        } catch (Exception e) {
            messageMetrics.recordMessageError(exchange, routingKey);
            log.error("Failed to send message to exchange: {} with routing key: {}", 
                exchange, routingKey, e);
            throw new MessageException("Failed to send message", e);
        }
    }

    /**
     * Send order message
     */
    public void sendOrderMessage(OrderMessage order) {
        sendMessage(RabbitMQConfig.ORDER_EXCHANGE, 
                   "order.created", 
                   order);
    }

    /**
     * Send notification message
     */
    public void sendNotificationMessage(NotificationMessage notification) {
        sendMessage(RabbitMQConfig.NOTIFICATION_EXCHANGE, 
                   "notification.send", 
                   notification);
    }
}

/**
 * Message exception
 */
class MessageException extends RuntimeException {
    
    public MessageException(String message) {
        super(message);
    }

    public MessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Message metrics
 */
@org.springframework.stereotype.Component
class MessageMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public MessageMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordMessageSent(String exchange, String routingKey, long duration) {
        registry.timer("rabbitmq.message.duration",
            "exchange", exchange,
            "routing_key", routingKey).record(java.time.Duration.ofMillis(duration));
        
        registry.counter("rabbitmq.message.sent",
            "exchange", exchange,
            "routing_key", routingKey).increment();
    }

    public void recordMessageError(String exchange, String routingKey) {
        registry.counter("rabbitmq.message.error",
            "exchange", exchange,
            "routing_key", routingKey).increment();
    }
}

/**
 * Message listeners
 */
@org.springframework.stereotype.Component
class OrderMessageListener {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderMessageListener.class);

    @org.springframework.amqp.rabbit.annotation.RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void handleOrderMessage(OrderMessage message) {
        try {
            log.info("Received order message: {}", message);
            // Process order message
        } catch (Exception e) {
            log.error("Failed to process order message", e);
            throw e;
        }
    }
}

@org.springframework.stereotype.Component
class NotificationMessageListener {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationMessageListener.class);

    @org.springframework.amqp.rabbit.annotation.RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handleNotificationMessage(NotificationMessage message) {
        try {
            log.info("Received notification message: {}", message);
            // Process notification message
        } catch (Exception e) {
            log.error("Failed to process notification message", e);
            throw e;
        }
    }
}
