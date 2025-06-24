package com.henuka.imitations.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;

@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
public class SessionConfig extends AbstractHttpSessionApplicationInitializer {

    /**
     * Configure cookie serializer
     */
    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSIONID");
        serializer.setCookiePath("/");
        serializer.setDomainNamePattern("^.+?\\.(\\w+\\.[a-z]+)$");
        serializer.setCookieMaxAge(3600);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }

    /**
     * Configure Redis template for session
     */
    @Bean
    public RedisTemplate<String, Object> sessionRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setDefaultSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}

/**
 * Session service
 */
@org.springframework.stereotype.Service
class SessionService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionService.class);

    public SessionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Get session attribute
     */
    public Object getAttribute(String sessionId, String key) {
        try {
            String redisKey = "spring:session:" + sessionId;
            return redisTemplate.opsForHash().get(redisKey, key);
        } catch (Exception e) {
            log.error("Failed to get session attribute", e);
            return null;
        }
    }

    /**
     * Set session attribute
     */
    public void setAttribute(String sessionId, String key, Object value) {
        try {
            String redisKey = "spring:session:" + sessionId;
            redisTemplate.opsForHash().put(redisKey, key, value);
        } catch (Exception e) {
            log.error("Failed to set session attribute", e);
        }
    }

    /**
     * Remove session attribute
     */
    public void removeAttribute(String sessionId, String key) {
        try {
            String redisKey = "spring:session:" + sessionId;
            redisTemplate.opsForHash().delete(redisKey, key);
        } catch (Exception e) {
            log.error("Failed to remove session attribute", e);
        }
    }

    /**
     * Get all session attributes
     */
    public java.util.Map<Object, Object> getAllAttributes(String sessionId) {
        try {
            String redisKey = "spring:session:" + sessionId;
            return redisTemplate.opsForHash().entries(redisKey);
        } catch (Exception e) {
            log.error("Failed to get all session attributes", e);
            return new java.util.HashMap<>();
        }
    }
}

/**
 * Session event listener
 */
@org.springframework.stereotype.Component
class SessionEventListener implements org.springframework.session.events.SessionCreatedEvent.SessionCreatedListener,
                                    org.springframework.session.events.SessionDestroyedEvent.SessionDestroyedListener,
                                    org.springframework.session.events.SessionExpiredEvent.SessionExpiredListener {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionEventListener.class);

    @Override
    public void onSessionCreated(org.springframework.session.events.SessionCreatedEvent event) {
        log.info("Session created: {}", event.getSessionId());
    }

    @Override
    public void onSessionDestroyed(org.springframework.session.events.SessionDestroyedEvent event) {
        log.info("Session destroyed: {}", event.getSessionId());
    }

    @Override
    public void onSessionExpired(org.springframework.session.events.SessionExpiredEvent event) {
        log.info("Session expired: {}", event.getSessionId());
    }
}

/**
 * Session interceptor
 */
@org.springframework.stereotype.Component
class SessionInterceptor implements org.springframework.web.servlet.HandlerInterceptor {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessionInterceptor.class);

    @Override
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                           jakarta.servlet.http.HttpServletResponse response,
                           Object handler) {
        String sessionId = request.getSession().getId();
        log.debug("Session ID: {} - URI: {}", sessionId, request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(jakarta.servlet.http.HttpServletRequest request,
                               jakarta.servlet.http.HttpServletResponse response,
                               Object handler, Exception ex) {
        if (ex != null) {
            log.error("Error processing request", ex);
        }
    }
}

/**
 * Session configuration for web security
 */
@org.springframework.context.annotation.Configuration
class SessionSecurityConfig {

    @Bean
    public org.springframework.security.web.session.HttpSessionEventPublisher httpSessionEventPublisher() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
    }

    @Bean
    public org.springframework.security.web.authentication.session.SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy(
            new org.springframework.security.core.session.SessionRegistryImpl());
    }
}
