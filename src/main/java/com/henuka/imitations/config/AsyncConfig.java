package com.henuka.imitations.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.AsyncTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Configure main task executor
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("AsyncTask-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Configure email task executor
     */
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("EmailTask-");
        executor.initialize();
        return executor;
    }

    /**
     * Configure notification task executor
     */
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(15);
        executor.setThreadNamePrefix("NotificationTask-");
        executor.initialize();
        return executor;
    }
}

/**
 * Async service
 */
@org.springframework.stereotype.Service
class AsyncService {
    
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AsyncMetrics asyncMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AsyncService.class);

    public AsyncService(EmailService emailService,
                       NotificationService notificationService,
                       AsyncMetrics asyncMetrics) {
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.asyncMetrics = asyncMetrics;
    }

    /**
     * Send email asynchronously
     */
    @org.springframework.scheduling.annotation.Async("emailExecutor")
    public java.util.concurrent.CompletableFuture<Boolean> sendEmailAsync(String to, String subject, String content) {
        try {
            long startTime = System.currentTimeMillis();
            emailService.sendTemplateEmail(to, subject, "generic", java.util.Collections.singletonMap("content", content));
            long duration = System.currentTimeMillis() - startTime;
            
            asyncMetrics.recordTaskDuration("email", duration);
            asyncMetrics.recordTaskSuccess("email");
            
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            asyncMetrics.recordTaskFailure("email");
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Send notification asynchronously
     */
    @org.springframework.scheduling.annotation.Async("notificationExecutor")
    public java.util.concurrent.CompletableFuture<Boolean> sendNotificationAsync(String userId, String message) {
        try {
            long startTime = System.currentTimeMillis();
            notificationService.sendNotification(userId, message);
            long duration = System.currentTimeMillis() - startTime;
            
            asyncMetrics.recordTaskDuration("notification", duration);
            asyncMetrics.recordTaskSuccess("notification");
            
            return java.util.concurrent.CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Failed to send notification to user: {}", userId, e);
            asyncMetrics.recordTaskFailure("notification");
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Process task asynchronously
     */
    @org.springframework.scheduling.annotation.Async("taskExecutor")
    public <T> java.util.concurrent.CompletableFuture<T> processAsync(java.util.function.Supplier<T> task) {
        try {
            long startTime = System.currentTimeMillis();
            T result = task.get();
            long duration = System.currentTimeMillis() - startTime;
            
            asyncMetrics.recordTaskDuration("general", duration);
            asyncMetrics.recordTaskSuccess("general");
            
            return java.util.concurrent.CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Async task failed", e);
            asyncMetrics.recordTaskFailure("general");
            throw new AsyncException("Async task failed", e);
        }
    }
}

/**
 * Async exception
 */
class AsyncException extends RuntimeException {
    
    public AsyncException(String message) {
        super(message);
    }

    public AsyncException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Async metrics
 */
@org.springframework.stereotype.Component
class AsyncMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public AsyncMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordTaskDuration(String type, long duration) {
        registry.timer("async.task.duration", "type", type)
                .record(java.time.Duration.ofMillis(duration));
    }

    public void recordTaskSuccess(String type) {
        registry.counter("async.task.success", "type", type).increment();
    }

    public void recordTaskFailure(String type) {
        registry.counter("async.task.failure", "type", type).increment();
    }

    public void recordQueueSize(String executor, int size) {
        registry.gauge("async.queue.size", 
            java.util.Collections.singletonList(
                io.micrometer.core.instrument.Tag.of("executor", executor)), size);
    }
}
