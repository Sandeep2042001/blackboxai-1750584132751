package com.henuka.imitations.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig implements SchedulingConfigurer {

    /**
     * Configure task scheduler
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskScheduler());
    }

    /**
     * Create task scheduler
     */
    @org.springframework.context.annotation.Bean(destroyMethod = "shutdown")
    public Executor taskScheduler() {
        return Executors.newScheduledThreadPool(10);
    }
}

/**
 * Scheduled tasks service
 */
@org.springframework.stereotype.Service
class ScheduledTasksService {
    
    private final ProductService productService;
    private final OrderService orderService;
    private final EmailService emailService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ScheduledTasksService.class);

    public ScheduledTasksService(ProductService productService,
                               OrderService orderService,
                               EmailService emailService) {
        this.productService = productService;
        this.orderService = orderService;
        this.emailService = emailService;
    }

    /**
     * Update product inventory daily
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 1 * * ?") // 1 AM daily
    public void updateProductInventory() {
        try {
            log.info("Starting daily inventory update");
            productService.updateInventory();
            log.info("Completed daily inventory update");
        } catch (Exception e) {
            log.error("Failed to update inventory", e);
        }
    }

    /**
     * Process pending orders every 5 minutes
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000) // 5 minutes
    public void processPendingOrders() {
        try {
            log.info("Processing pending orders");
            orderService.processPendingOrders();
            log.info("Completed processing pending orders");
        } catch (Exception e) {
            log.error("Failed to process pending orders", e);
        }
    }

    /**
     * Send daily sales report
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 6 * * ?") // 6 AM daily
    public void sendDailySalesReport() {
        try {
            log.info("Generating daily sales report");
            java.util.Map<String, Object> reportData = orderService.generateDailySalesReport();
            emailService.sendTemplateEmail(
                "reports@henukaimitations.com",
                "Daily Sales Report",
                "daily-sales-report",
                reportData
            );
            log.info("Daily sales report sent");
        } catch (Exception e) {
            log.error("Failed to send daily sales report", e);
        }
    }

    /**
     * Clean expired sessions weekly
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * SUN") // 2 AM every Sunday
    public void cleanExpiredSessions() {
        try {
            log.info("Cleaning expired sessions");
            // Implementation
            log.info("Completed cleaning expired sessions");
        } catch (Exception e) {
            log.error("Failed to clean expired sessions", e);
        }
    }
}

/**
 * Task execution metrics
 */
@org.springframework.stereotype.Component
class TaskMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public TaskMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordTaskExecution(String taskName, long duration) {
        registry.timer("scheduled.task.duration", "task", taskName)
                .record(java.time.Duration.ofMillis(duration));
    }

    public void recordTaskSuccess(String taskName) {
        registry.counter("scheduled.task.success", "task", taskName)
                .increment();
    }

    public void recordTaskFailure(String taskName) {
        registry.counter("scheduled.task.failure", "task", taskName)
                .increment();
    }
}

/**
 * Task execution aspect
 */
@org.springframework.stereotype.Component
@org.aspectj.lang.annotation.Aspect
class TaskExecutionAspect {
    
    private final TaskMetrics taskMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TaskExecutionAspect.class);

    public TaskExecutionAspect(TaskMetrics taskMetrics) {
        this.taskMetrics = taskMetrics;
    }

    @org.aspectj.lang.annotation.Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object measureTaskExecution(org.aspectj.lang.ProceedingJoinPoint joinPoint) throws Throwable {
        String taskName = joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            taskMetrics.recordTaskSuccess(taskName);
            return result;
        } catch (Exception e) {
            taskMetrics.recordTaskFailure(taskName);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            taskMetrics.recordTaskExecution(taskName, duration);
            log.debug("Task {} completed in {}ms", taskName, duration);
        }
    }
}
