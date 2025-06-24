package com.henuka.imitations.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public RateLimitConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
    }
}

/**
 * Rate limit interceptor
 */
@org.springframework.stereotype.Component
class RateLimitInterceptor implements org.springframework.web.servlet.HandlerInterceptor {
    
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitMetrics rateLimitMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimitInterceptor.class);

    public RateLimitInterceptor(RateLimitMetrics rateLimitMetrics) {
        this.rateLimitMetrics = rateLimitMetrics;
    }

    @Override
    public boolean preHandle(javax.servlet.http.HttpServletRequest request,
                           javax.servlet.http.HttpServletResponse response,
                           Object handler) throws Exception {
        String clientId = getClientId(request);
        Bucket bucket = buckets.computeIfAbsent(clientId, this::createBucket);

        if (bucket.tryConsume(1)) {
            rateLimitMetrics.recordAllowed(request.getRequestURI());
            return true;
        }

        rateLimitMetrics.recordRejected(request.getRequestURI());
        response.setStatus(429);
        response.setHeader("X-RateLimit-Retry-After", String.valueOf(bucket.getNextRefillTime()));
        return false;
    }

    private String getClientId(javax.servlet.http.HttpServletRequest request) {
        String clientId = request.getHeader("X-Client-ID");
        if (clientId == null) {
            clientId = request.getRemoteAddr();
        }
        return clientId;
    }

    private Bucket createBucket(String clientId) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
                .addLimit(Bandwidth.classic(1000, Refill.intervally(1000, Duration.ofHours(1))))
                .build();
    }
}

/**
 * Rate limit service
 */
@org.springframework.stereotype.Service
class RateLimitService {
    
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitMetrics rateLimitMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimitService.class);

    public RateLimitService(RateLimitMetrics rateLimitMetrics) {
        this.rateLimitMetrics = rateLimitMetrics;
    }

    /**
     * Check if operation is allowed
     */
    public boolean isAllowed(String key, RateLimitRule rule) {
        try {
            Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(rule));
            boolean allowed = bucket.tryConsume(1);
            
            if (allowed) {
                rateLimitMetrics.recordAllowed(rule.getName());
            } else {
                rateLimitMetrics.recordRejected(rule.getName());
            }
            
            return allowed;
        } catch (Exception e) {
            log.error("Rate limit check failed for key: {}", key, e);
            rateLimitMetrics.recordError(rule.getName());
            return false;
        }
    }

    private Bucket createBucket(RateLimitRule rule) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(rule.getTokens(), 
                    Refill.intervally(rule.getTokens(), rule.getInterval())))
                .build();
    }

    /**
     * Clear expired buckets
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 3600000) // Every hour
    public void clearExpiredBuckets() {
        try {
            long startTime = System.currentTimeMillis();
            int cleared = 0;
            
            for (String key : buckets.keySet()) {
                Bucket bucket = buckets.get(key);
                if (bucket.getAvailableTokens() == bucket.getCapacity()) {
                    buckets.remove(key);
                    cleared++;
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            rateLimitMetrics.recordCleanup(cleared, duration);
            
            log.debug("Cleared {} expired rate limit buckets", cleared);
        } catch (Exception e) {
            log.error("Failed to clear expired buckets", e);
            rateLimitMetrics.recordError("cleanup");
        }
    }
}

/**
 * Rate limit rule
 */
class RateLimitRule {
    private final String name;
    private final int tokens;
    private final Duration interval;

    public RateLimitRule(String name, int tokens, Duration interval) {
        this.name = name;
        this.tokens = tokens;
        this.interval = interval;
    }

    public String getName() { return name; }
    public int getTokens() { return tokens; }
    public Duration getInterval() { return interval; }
}

/**
 * Rate limit exception
 */
class RateLimitException extends RuntimeException {
    
    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Rate limit metrics
 */
@org.springframework.stereotype.Component
class RateLimitMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public RateLimitMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordAllowed(String operation) {
        registry.counter("ratelimit.requests.allowed",
            "operation", operation).increment();
    }

    public void recordRejected(String operation) {
        registry.counter("ratelimit.requests.rejected",
            "operation", operation).increment();
    }

    public void recordError(String operation) {
        registry.counter("ratelimit.error",
            "operation", operation).increment();
    }

    public void recordCleanup(int count, long duration) {
        registry.counter("ratelimit.cleanup.count").increment(count);
        registry.timer("ratelimit.cleanup.duration")
                .record(java.time.Duration.ofMillis(duration));
    }
}
