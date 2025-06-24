package com.henuka.imitations.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class ResilienceConfig {

    /**
     * Configure circuit breaker registry
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    /**
     * Configure retry registry
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(Exception.class)
                .build();
        return RetryRegistry.of(config);
    }

    /**
     * Configure bulkhead registry
     */
    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .maxWaitDuration(Duration.ofMillis(500))
                .build();
        return BulkheadRegistry.of(config);
    }
}

/**
 * Resilience service
 */
@org.springframework.stereotype.Service
class ResilienceService {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final ResilienceMetrics resilienceMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ResilienceService.class);

    public ResilienceService(CircuitBreakerRegistry circuitBreakerRegistry,
                           RetryRegistry retryRegistry,
                           BulkheadRegistry bulkheadRegistry,
                           ResilienceMetrics resilienceMetrics) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.resilienceMetrics = resilienceMetrics;
    }

    /**
     * Execute with circuit breaker
     */
    public <T> T executeWithCircuitBreaker(String name, java.util.function.Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        
        return CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            long startTime = System.currentTimeMillis();
            try {
                T result = supplier.get();
                long duration = System.currentTimeMillis() - startTime;
                resilienceMetrics.recordSuccess("circuit_breaker", name, duration);
                return result;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                resilienceMetrics.recordError("circuit_breaker", name, duration);
                throw e;
            }
        }).get();
    }

    /**
     * Execute with retry
     */
    public <T> T executeWithRetry(String name, java.util.function.Supplier<T> supplier) {
        Retry retry = retryRegistry.retry(name);
        
        return Retry.decorateSupplier(retry, () -> {
            long startTime = System.currentTimeMillis();
            try {
                T result = supplier.get();
                long duration = System.currentTimeMillis() - startTime;
                resilienceMetrics.recordSuccess("retry", name, duration);
                return result;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                resilienceMetrics.recordError("retry", name, duration);
                throw e;
            }
        }).get();
    }

    /**
     * Execute with bulkhead
     */
    public <T> T executeWithBulkhead(String name, java.util.function.Supplier<T> supplier) {
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(name);
        
        return Bulkhead.decorateSupplier(bulkhead, () -> {
            long startTime = System.currentTimeMillis();
            try {
                T result = supplier.get();
                long duration = System.currentTimeMillis() - startTime;
                resilienceMetrics.recordSuccess("bulkhead", name, duration);
                return result;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                resilienceMetrics.recordError("bulkhead", name, duration);
                throw e;
            }
        }).get();
    }
}

/**
 * Resilience aspect
 */
@org.springframework.stereotype.Component
@org.aspectj.lang.annotation.Aspect
class ResilienceAspect {
    
    private final ResilienceService resilienceService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ResilienceAspect.class);

    public ResilienceAspect(ResilienceService resilienceService) {
        this.resilienceService = resilienceService;
    }

    @org.aspectj.lang.annotation.Around("@annotation(com.henuka.imitations.annotation.CircuitBreaker)")
    public Object circuitBreaker(org.aspectj.lang.ProceedingJoinPoint joinPoint) throws Throwable {
        String name = joinPoint.getSignature().getName();
        return resilienceService.executeWithCircuitBreaker(name, () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    @org.aspectj.lang.annotation.Around("@annotation(com.henuka.imitations.annotation.Retry)")
    public Object retry(org.aspectj.lang.ProceedingJoinPoint joinPoint) throws Throwable {
        String name = joinPoint.getSignature().getName();
        return resilienceService.executeWithRetry(name, () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    @org.aspectj.lang.annotation.Around("@annotation(com.henuka.imitations.annotation.Bulkhead)")
    public Object bulkhead(org.aspectj.lang.ProceedingJoinPoint joinPoint) throws Throwable {
        String name = joinPoint.getSignature().getName();
        return resilienceService.executeWithBulkhead(name, () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}

/**
 * Resilience metrics
 */
@org.springframework.stereotype.Component
class ResilienceMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public ResilienceMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSuccess(String type, String name, long duration) {
        registry.timer("resilience.execution",
            "type", type,
            "name", name,
            "result", "success")
            .record(java.time.Duration.ofMillis(duration));
    }

    public void recordError(String type, String name, long duration) {
        registry.timer("resilience.execution",
            "type", type,
            "name", name,
            "result", "error")
            .record(java.time.Duration.ofMillis(duration));
    }
}
