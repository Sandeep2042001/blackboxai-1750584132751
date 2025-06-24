package com.henuka.imitations.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Value("${spring.redis.password}")
    private String redisPassword;

    /**
     * Configure Redis connection factory
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }
        return new LettuceConnectionFactory(config);
    }

    /**
     * Configure Redis template
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setEnableTransactionSupport(true);
        return template;
    }
}

/**
 * Redis service
 */
@org.springframework.stereotype.Service
class RedisService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMetrics redisMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisService.class);

    public RedisService(RedisTemplate<String, Object> redisTemplate,
                       RedisMetrics redisMetrics) {
        this.redisTemplate = redisTemplate;
        this.redisMetrics = redisMetrics;
    }

    /**
     * Set value with expiration
     */
    public void setValue(String key, Object value, long timeoutSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, java.time.Duration.ofSeconds(timeoutSeconds));
            redisMetrics.recordOperation("set", true);
        } catch (Exception e) {
            log.error("Failed to set value for key: {}", key, e);
            redisMetrics.recordOperation("set", false);
            throw new RedisOperationException("Failed to set value", e);
        }
    }

    /**
     * Get value
     */
    public <T> T getValue(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            redisMetrics.recordOperation("get", true);
            return value != null ? type.cast(value) : null;
        } catch (Exception e) {
            log.error("Failed to get value for key: {}", key, e);
            redisMetrics.recordOperation("get", false);
            throw new RedisOperationException("Failed to get value", e);
        }
    }

    /**
     * Delete value
     */
    public void deleteValue(String key) {
        try {
            redisTemplate.delete(key);
            redisMetrics.recordOperation("delete", true);
        } catch (Exception e) {
            log.error("Failed to delete value for key: {}", key, e);
            redisMetrics.recordOperation("delete", false);
            throw new RedisOperationException("Failed to delete value", e);
        }
    }

    /**
     * Increment value
     */
    public Long increment(String key) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            redisMetrics.recordOperation("increment", true);
            return value;
        } catch (Exception e) {
            log.error("Failed to increment value for key: {}", key, e);
            redisMetrics.recordOperation("increment", false);
            throw new RedisOperationException("Failed to increment value", e);
        }
    }

    /**
     * Add to set
     */
    public void addToSet(String key, Object... values) {
        try {
            redisTemplate.opsForSet().add(key, values);
            redisMetrics.recordOperation("set_add", true);
        } catch (Exception e) {
            log.error("Failed to add to set: {}", key, e);
            redisMetrics.recordOperation("set_add", false);
            throw new RedisOperationException("Failed to add to set", e);
        }
    }

    /**
     * Get set members
     */
    public <T> java.util.Set<T> getSetMembers(String key, Class<T> type) {
        try {
            java.util.Set<Object> members = redisTemplate.opsForSet().members(key);
            redisMetrics.recordOperation("set_members", true);
            return members.stream()
                    .map(type::cast)
                    .collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            log.error("Failed to get set members: {}", key, e);
            redisMetrics.recordOperation("set_members", false);
            throw new RedisOperationException("Failed to get set members", e);
        }
    }
}

/**
 * Redis operation exception
 */
class RedisOperationException extends RuntimeException {
    
    public RedisOperationException(String message) {
        super(message);
    }

    public RedisOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Redis metrics
 */
@org.springframework.stereotype.Component
class RedisMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public RedisMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordOperation(String operation, boolean success) {
        registry.counter("redis.operation", 
            "operation", operation,
            "status", success ? "success" : "failure").increment();
    }

    public void recordConnectionStatus(boolean connected) {
        registry.gauge("redis.connected", 
            connected ? 1 : 0);
    }
}
