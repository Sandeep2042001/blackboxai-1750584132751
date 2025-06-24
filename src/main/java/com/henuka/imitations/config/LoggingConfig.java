package com.henuka.imitations.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import jakarta.annotation.PostConstruct;

@Configuration
public class LoggingConfig {

    private static final String LOG_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";
    private static final String LOG_FILE = "logs/application.log";
    private static final String LOG_FILE_PATTERN = "logs/archived/application.%d{yyyy-MM-dd}.log";

    @PostConstruct
    public void init() {
        configureLogback();
    }

    private void configureLogback() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        // Console appender
        ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setName("console");
        
        PatternLayoutEncoder consoleEncoder = new PatternLayoutEncoder();
        consoleEncoder.setContext(context);
        consoleEncoder.setPattern(LOG_PATTERN);
        consoleEncoder.start();
        
        consoleAppender.setEncoder(consoleEncoder);
        consoleAppender.start();

        // File appender
        RollingFileAppender<ch.qos.logback.classic.spi.ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setName("file");
        fileAppender.setFile(LOG_FILE);
        
        TimeBasedRollingPolicy<ch.qos.logback.classic.spi.ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(LOG_FILE_PATTERN);
        rollingPolicy.setMaxHistory(30);
        rollingPolicy.start();
        
        PatternLayoutEncoder fileEncoder = new PatternLayoutEncoder();
        fileEncoder.setContext(context);
        fileEncoder.setPattern(LOG_PATTERN);
        fileEncoder.start();
        
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.setEncoder(fileEncoder);
        fileAppender.start();

        // Root logger
        ch.qos.logback.classic.Logger rootLogger = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(ch.qos.logback.classic.Level.INFO);
        rootLogger.addAppender(consoleAppender);
        rootLogger.addAppender(fileAppender);
    }

    @Bean
    public LoggingService loggingService() {
        return new LoggingService();
    }
}

/**
 * Logging service
 */
@org.springframework.stereotype.Service
class LoggingService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoggingService.class);

    /**
     * Log application event
     */
    public void logEvent(String event, String details) {
        try {
            log.info("Event: {} - Details: {}", event, details);
        } catch (Exception e) {
            log.error("Failed to log event", e);
        }
    }

    /**
     * Log error with stack trace
     */
    public void logError(String message, Throwable error) {
        try {
            log.error(message, error);
        } catch (Exception e) {
            log.error("Failed to log error", e);
        }
    }

    /**
     * Log security event
     */
    public void logSecurityEvent(String event, String username) {
        try {
            log.info("Security Event: {} - User: {}", event, username);
        } catch (Exception e) {
            log.error("Failed to log security event", e);
        }
    }

    /**
     * Log performance metric
     */
    public void logPerformance(String operation, long durationMs) {
        try {
            log.info("Performance - Operation: {} - Duration: {}ms", operation, durationMs);
        } catch (Exception e) {
            log.error("Failed to log performance metric", e);
        }
    }
}

/**
 * Logging aspect for method execution
 */
@org.springframework.stereotype.Component
@org.aspectj.lang.annotation.Aspect
class LoggingAspect {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoggingAspect.class);

    @org.aspectj.lang.annotation.Around("@annotation(org.springframework.web.bind.annotation.RequestMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public Object logMethod(org.aspectj.lang.ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        log.info("Starting {} in {}", methodName, className);
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Completed {} in {} - Duration: {}ms", methodName, className, duration);
            return result;
        } catch (Exception e) {
            log.error("Error in {} - {}: {}", className, methodName, e.getMessage());
            throw e;
        }
    }
}

/**
 * MDC filter for request tracking
 */
@org.springframework.stereotype.Component
class RequestLoggingFilter implements jakarta.servlet.Filter {
    
    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, 
                        jakarta.servlet.ServletResponse response,
                        jakarta.servlet.FilterChain chain) 
            throws java.io.IOException, jakarta.servlet.ServletException {
        
        try {
            org.slf4j.MDC.put("requestId", java.util.UUID.randomUUID().toString());
            org.slf4j.MDC.put("remoteAddr", request.getRemoteAddr());
            
            if (request instanceof jakarta.servlet.http.HttpServletRequest) {
                jakarta.servlet.http.HttpServletRequest httpRequest = 
                    (jakarta.servlet.http.HttpServletRequest) request;
                org.slf4j.MDC.put("method", httpRequest.getMethod());
                org.slf4j.MDC.put("uri", httpRequest.getRequestURI());
            }
            
            chain.doFilter(request, response);
        } finally {
            org.slf4j.MDC.clear();
        }
    }
}
