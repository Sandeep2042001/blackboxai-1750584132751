package com.henuka.imitations.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MonitoringConfig {

    /**
     * Configure JVM metrics
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    @Bean
    public UptimeMetrics uptimeMetrics() {
        return new UptimeMetrics();
    }

    /**
     * Custom health indicator for database
     */
    @Bean
    public HealthIndicator databaseHealthIndicator(javax.sql.DataSource dataSource) {
        return new DatabaseHealthIndicator(dataSource);
    }

    /**
     * Custom health indicator for Redis
     */
    @Bean
    public HealthIndicator redisHealthIndicator(
            org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory) {
        return new RedisHealthIndicator(redisConnectionFactory);
    }
}

/**
 * Database health indicator
 */
class DatabaseHealthIndicator implements HealthIndicator {
    
    private final javax.sql.DataSource dataSource;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseHealthIndicator.class);

    public DatabaseHealthIndicator(javax.sql.DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public org.springframework.boot.actuate.health.Health health() {
        try (java.sql.Connection conn = dataSource.getConnection()) {
            java.sql.DatabaseMetaData metadata = conn.getMetaData();
            return org.springframework.boot.actuate.health.Health.up()
                    .withDetail("database", metadata.getDatabaseProductName())
                    .withDetail("version", metadata.getDatabaseProductVersion())
                    .build();
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return org.springframework.boot.actuate.health.Health.down()
                    .withException(e)
                    .build();
        }
    }
}

/**
 * Redis health indicator
 */
class RedisHealthIndicator implements HealthIndicator {
    
    private final org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisHealthIndicator.class);

    public RedisHealthIndicator(
            org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public org.springframework.boot.actuate.health.Health health() {
        try {
            org.springframework.data.redis.connection.RedisConnection conn = 
                redisConnectionFactory.getConnection();
            String version = new String(conn.info().getProperty("redis_version"));
            return org.springframework.boot.actuate.health.Health.up()
                    .withDetail("version", version)
                    .build();
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return org.springframework.boot.actuate.health.Health.down()
                    .withException(e)
                    .build();
        }
    }
}

/**
 * Application metrics service
 */
@org.springframework.stereotype.Service
class ApplicationMetricsService {
    
    private final MeterRegistry meterRegistry;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApplicationMetricsService.class);

    public ApplicationMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record request timing
     */
    public void recordRequestTiming(String endpoint, long duration) {
        try {
            meterRegistry.timer("http.request.duration", 
                "endpoint", endpoint).record(java.time.Duration.ofMillis(duration));
        } catch (Exception e) {
            log.error("Failed to record request timing", e);
        }
    }

    /**
     * Record business operation
     */
    public void recordBusinessOperation(String operation, String status) {
        try {
            meterRegistry.counter("business.operation", 
                "operation", operation,
                "status", status).increment();
        } catch (Exception e) {
            log.error("Failed to record business operation", e);
        }
    }

    /**
     * Record active users
     */
    public void recordActiveUsers(int count) {
        try {
            meterRegistry.gauge("users.active", count);
        } catch (Exception e) {
            log.error("Failed to record active users", e);
        }
    }
}

/**
 * Request timing aspect
 */
@org.springframework.stereotype.Component
@org.aspectj.lang.annotation.Aspect
class RequestTimingAspect {
    
    private final ApplicationMetricsService metricsService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RequestTimingAspect.class);

    public RequestTimingAspect(ApplicationMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @org.aspectj.lang.annotation.Around("@annotation(org.springframework.web.bind.annotation.RequestMapping)")
    public Object measureRequestTiming(org.aspectj.lang.ProceedingJoinPoint joinPoint) throws Throwable {
        String endpoint = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();
        
        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordRequestTiming(endpoint, duration);
            log.debug("Request {} completed in {}ms", endpoint, duration);
        }
    }
}

/**
 * Custom metrics endpoint
 */
@org.springframework.web.bind.annotation.RestController
@org.springframework.web.bind.annotation.RequestMapping("/actuator/custom-metrics")
class CustomMetricsEndpoint {
    
    private final ApplicationMetricsService metricsService;
    private final MeterRegistry meterRegistry;

    public CustomMetricsEndpoint(ApplicationMetricsService metricsService,
                                MeterRegistry meterRegistry) {
        this.metricsService = metricsService;
        this.meterRegistry = meterRegistry;
    }

    @org.springframework.web.bind.annotation.GetMapping
    public java.util.Map<String, Object> getCustomMetrics() {
        java.util.Map<String, Object> metrics = new java.util.HashMap<>();
        
        // Add custom metrics
        metrics.put("activeUsers", meterRegistry.get("users.active").gauge().value());
        metrics.put("requestCount", meterRegistry.get("http.request.duration").timer().count());
        metrics.put("businessOperations", meterRegistry.get("business.operation").counter().count());
        
        return metrics;
    }
}
