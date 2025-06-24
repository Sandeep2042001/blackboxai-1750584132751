package com.henuka.imitations.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketMetrics webSocketMetrics;

    public WebSocketConfig(WebSocketMetrics webSocketMetrics) {
        this.webSocketMetrics = webSocketMetrics;
    }

    /**
     * Configure message broker
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Configure WebSocket endpoints
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS();
    }

    /**
     * Configure WebSocket transport options
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(new WebSocketHandlerDecoratorFactory() {
            @Override
            public WebSocketHandler decorate(WebSocketHandler handler) {
                return new WebSocketHandlerDecorator(handler) {
                    @Override
                    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                        webSocketMetrics.recordConnection();
                        super.afterConnectionEstablished(session);
                    }

                    @Override
                    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                        webSocketMetrics.recordDisconnection();
                        super.afterConnectionClosed(session, closeStatus);
                    }
                };
            }
        });
    }
}

/**
 * WebSocket service
 */
@org.springframework.stereotype.Service
class WebSocketService {
    
    private final SimpMessageSendingOperations messagingTemplate;
    private final WebSocketMetrics webSocketMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketService.class);

    public WebSocketService(SimpMessageSendingOperations messagingTemplate,
                          WebSocketMetrics webSocketMetrics) {
        this.messagingTemplate = messagingTemplate;
        this.webSocketMetrics = webSocketMetrics;
    }

    /**
     * Send message to topic
     */
    public void sendToTopic(String topic, Object message) {
        try {
            long startTime = System.currentTimeMillis();
            messagingTemplate.convertAndSend("/topic/" + topic, message);
            long duration = System.currentTimeMillis() - startTime;
            
            webSocketMetrics.recordMessageSent("topic", duration);
            log.debug("Message sent to topic: {}", topic);
        } catch (Exception e) {
            webSocketMetrics.recordMessageError("topic");
            log.error("Failed to send message to topic: {}", topic, e);
            throw new WebSocketException("Failed to send message to topic", e);
        }
    }

    /**
     * Send message to user
     */
    public void sendToUser(String userId, String destination, Object message) {
        try {
            long startTime = System.currentTimeMillis();
            messagingTemplate.convertAndSendToUser(userId, "/queue/" + destination, message);
            long duration = System.currentTimeMillis() - startTime;
            
            webSocketMetrics.recordMessageSent("user", duration);
            log.debug("Message sent to user: {} at destination: {}", userId, destination);
        } catch (Exception e) {
            webSocketMetrics.recordMessageError("user");
            log.error("Failed to send message to user: {} at destination: {}", userId, destination, e);
            throw new WebSocketException("Failed to send message to user", e);
        }
    }

    /**
     * Broadcast message
     */
    public void broadcast(String destination, Object message) {
        try {
            long startTime = System.currentTimeMillis();
            messagingTemplate.convertAndSend(destination, message);
            long duration = System.currentTimeMillis() - startTime;
            
            webSocketMetrics.recordMessageSent("broadcast", duration);
            log.debug("Message broadcasted to: {}", destination);
        } catch (Exception e) {
            webSocketMetrics.recordMessageError("broadcast");
            log.error("Failed to broadcast message to: {}", destination, e);
            throw new WebSocketException("Failed to broadcast message", e);
        }
    }
}

/**
 * WebSocket message handler
 */
@org.springframework.stereotype.Component
class WebSocketMessageHandler {
    
    private final WebSocketMetrics webSocketMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketMessageHandler.class);

    public WebSocketMessageHandler(WebSocketMetrics webSocketMetrics) {
        this.webSocketMetrics = webSocketMetrics;
    }

    @org.springframework.messaging.handler.annotation.MessageMapping("/send/{topic}")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/{topic}")
    public Object handleMessage(@org.springframework.messaging.handler.annotation.DestinationVariable String topic,
                              @org.springframework.messaging.handler.annotation.Payload Object message,
                              StompHeaderAccessor headerAccessor) {
        try {
            long startTime = System.currentTimeMillis();
            // Process message
            long duration = System.currentTimeMillis() - startTime;
            
            webSocketMetrics.recordMessageReceived("topic", duration);
            log.debug("Message received for topic: {}", topic);
            
            return message;
        } catch (Exception e) {
            webSocketMetrics.recordMessageError("receive");
            log.error("Failed to handle message for topic: {}", topic, e);
            throw new WebSocketException("Failed to handle message", e);
        }
    }
}

/**
 * WebSocket exception
 */
class WebSocketException extends RuntimeException {
    
    public WebSocketException(String message) {
        super(message);
    }

    public WebSocketException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * WebSocket metrics
 */
@org.springframework.stereotype.Component
class WebSocketMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public WebSocketMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordConnection() {
        registry.counter("websocket.connections").increment();
    }

    public void recordDisconnection() {
        registry.counter("websocket.disconnections").increment();
    }

    public void recordMessageSent(String type, long duration) {
        registry.timer("websocket.message.sent.duration",
            "type", type).record(java.time.Duration.ofMillis(duration));
        
        registry.counter("websocket.message.sent",
            "type", type).increment();
    }

    public void recordMessageReceived(String type, long duration) {
        registry.timer("websocket.message.received.duration",
            "type", type).record(java.time.Duration.ofMillis(duration));
        
        registry.counter("websocket.message.received",
            "type", type).increment();
    }

    public void recordMessageError(String operation) {
        registry.counter("websocket.error",
            "operation", operation).increment();
    }
}
