package com.henuka.imitations.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

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
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    @Bean
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics();
    }

    /**
     * Configure system metrics
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    @Bean
    public UptimeMetrics uptimeMetrics() {
        return new UptimeMetrics();
    }
}

/**
 * Application metrics service
 */
@org.springframework.stereotype.Service
class ApplicationMetricsService {
    
    private final MeterRegistry registry;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApplicationMetricsService.class);

    public ApplicationMetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Record HTTP request
     */
    public void recordHttpRequest(String method, String path, int status, long duration) {
        try {
            registry.timer("http.server.requests",
                "method", method,
                "path", path,
                "status", String.valueOf(status))
                .record(java.time.Duration.ofMillis(duration));
        } catch (Exception e) {
            log.error("Failed to record HTTP request metric", e);
        }
    }

    /**
     * Record business operation
     */
    public void recordBusinessOperation(String operation, String status, long duration) {
        try {
            registry.timer("business.operation",
                "operation", operation,
                "status", status)
                .record(java.time.Duration.ofMillis(duration));
        } catch (Exception e) {
            log.error("Failed to record business operation metric", e);
        }
    }

    /**
     * Record active users
     */
    public void recordActiveUsers(int count) {
        try {
            registry.gauge("users.active", count);
        } catch (Exception e) {
            log.error("Failed to record active users metric", e);
        }
    }

    /**
     * Record database operation
     */
    public void recordDatabaseOperation(String operation, String status, long duration) {
        try {
            registry.timer("database.operation",
                "operation", operation,
                "status", status)
                .record(java.time.Duration.ofMillis(duration));
        } catch (Exception e) {
            log.error("Failed to record database operation metric", e);
        }
    }

    /**
     * Record cache operation
     */
    public void recordCacheOperation(String operation, boolean hit, long duration) {
        try {
            registry.timer("cache.operation",
                "operation", operation,
                "result", hit ? "hit" : "miss")
                .record(java.time.Duration.ofMillis(duration));
        } catch (Exception e) {
            log.error("Failed to record cache operation metric", e);
        }
    }
}

/**
 * HTTP request metrics aspect
 */
@org.springframework.stereotype.Component
@org.aspectj.lang.annotation.Aspect
class HttpRequestMetricsAspect {
    
    private final ApplicationMetricsService metricsService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HttpRequestMetricsAspect.class);

    public HttpRequestMetricsAspect(ApplicationMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @org.aspectj.lang.annotation.Around("@annotation(org.springframework.web.bind.annotation.RequestMapping)")
    public Object measureHttpRequest(org.aspectj.lang.ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            org.springframework.web.context.request.RequestAttributes requestAttributes = 
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            
            if (requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                javax.servlet.http.HttpServletRequest request = 
                    ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes).getRequest();
                
                metricsService.recordHttpRequest(
                    request.getMethod(),
                    request.getRequestURI(),
                    200,
                    duration
                );
            }
            
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            metricsService.recordHttpRequest(
                "UNKNOWN",
                "ERROR",
                500,
                duration
            );
            
            throw e;
        }
    }
}

/**
 * Business operation metrics aspect
 */
@org.springframework.stereotype.Component
@org.aspectj.lang.annotation.Aspect
class BusinessMetricsAspect {
    
    private final ApplicationMetricsService metricsService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BusinessMetricsAspect.class);

    public BusinessMetricsAspect(ApplicationMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @org.aspectj.lang.annotation.Around("@annotation(com.henuka.imitations.annotation.Measured)")
    public Object measureBusinessOperation(org.aspectj.lang.ProceedingJoinPoint joinPoint) throws Throwable {
        String operation = joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            metricsService.recordBusinessOperation(operation, "success", duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordBusinessOperation(operation, "error", duration);
            throw e;
        }
    }
}
