package com.henuka.imitations.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditConfig {

    /**
     * Configure auditor provider
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new SpringSecurityAuditorAware();
    }
}

/**
 * Spring Security auditor aware
 */
class SpringSecurityAuditorAware implements AuditorAware<String> {
    
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of("SYSTEM");
        }
        
        return Optional.of(authentication.getName());
    }
}

/**
 * Audit service
 */
@org.springframework.stereotype.Service
class AuditService {
    
    private final AuditRepository auditRepository;
    private final AuditMetrics auditMetrics;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditService.class);

    public AuditService(AuditRepository auditRepository,
                       AuditMetrics auditMetrics) {
        this.auditRepository = auditRepository;
        this.auditMetrics = auditMetrics;
    }

    /**
     * Create audit log
     */
    public void createAuditLog(String action, String entityType, String entityId, String details) {
        try {
            long startTime = System.currentTimeMillis();
            
            AuditLog auditLog = new AuditLog();
            auditLog.setAction(action);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setDetails(details);
            auditLog.setTimestamp(LocalDateTime.now());
            
            auditRepository.save(auditLog);
            
            long duration = System.currentTimeMillis() - startTime;
            auditMetrics.recordAuditLog(action, duration);
            
            log.debug("Created audit log: {} - {}", action, entityId);
        } catch (Exception e) {
            auditMetrics.recordError(action);
            log.error("Failed to create audit log: {} - {}", action, entityId, e);
            throw new AuditException("Failed to create audit log", e);
        }
    }

    /**
     * Search audit logs
     */
    public java.util.List<AuditLog> searchAuditLogs(String entityType,
                                                   String entityId,
                                                   LocalDateTime startDate,
                                                   LocalDateTime endDate) {
        try {
            long startTime = System.currentTimeMillis();
            
            java.util.List<AuditLog> logs = auditRepository.findByEntityTypeAndEntityIdAndTimestampBetween(
                entityType, entityId, startDate, endDate);
            
            long duration = System.currentTimeMillis() - startTime;
            auditMetrics.recordSearch(duration);
            
            return logs;
        } catch (Exception e) {
            auditMetrics.recordError("search");
            log.error("Failed to search audit logs", e);
            throw new AuditException("Failed to search audit logs", e);
        }
    }
}

/**
 * Audit log entity
 */
@javax.persistence.Entity
@javax.persistence.Table(name = "audit_logs")
class AuditLog {
    
    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.IDENTITY)
    private Long id;

    @javax.persistence.Column(nullable = false)
    private String action;

    @javax.persistence.Column(nullable = false)
    private String entityType;

    @javax.persistence.Column(nullable = false)
    private String entityId;

    @javax.persistence.Column(columnDefinition = "TEXT")
    private String details;

    @javax.persistence.Column(nullable = false)
    private LocalDateTime timestamp;

    @javax.persistence.Column(nullable = false)
    private String createdBy;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}

/**
 * Audit repository
 */
@org.springframework.stereotype.Repository
interface AuditRepository extends org.springframework.data.jpa.repository.JpaRepository<AuditLog, Long> {
    
    java.util.List<AuditLog> findByEntityTypeAndEntityIdAndTimestampBetween(
        String entityType, String entityId, LocalDateTime startDate, LocalDateTime endDate);
}

/**
 * Audit aspect
 */
@org.springframework.stereotype.Component
@org.aspectj.lang.annotation.Aspect
class AuditAspect {
    
    private final AuditService auditService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditAspect.class);

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @org.aspectj.lang.annotation.AfterReturning("@annotation(com.henuka.imitations.annotation.Audited)")
    public void auditMethod(org.aspectj.lang.JoinPoint joinPoint) {
        try {
            String methodName = joinPoint.getSignature().getName();
            Object[] args = joinPoint.getArgs();
            
            auditService.createAuditLog(
                methodName,
                "METHOD",
                methodName,
                "Method executed with args: " + java.util.Arrays.toString(args)
            );
        } catch (Exception e) {
            log.error("Failed to create method audit log", e);
        }
    }
}

/**
 * Audit exception
 */
class AuditException extends RuntimeException {
    
    public AuditException(String message) {
        super(message);
    }

    public AuditException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Audit metrics
 */
@org.springframework.stereotype.Component
class AuditMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;

    public AuditMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordAuditLog(String action, long duration) {
        registry.timer("audit.log.duration",
            "action", action).record(java.time.Duration.ofMillis(duration));
        
        registry.counter("audit.log.count",
            "action", action).increment();
    }

    public void recordSearch(long duration) {
        registry.timer("audit.search.duration")
                .record(java.time.Duration.ofMillis(duration));
    }

    public void recordError(String operation) {
        registry.counter("audit.error",
            "operation", operation).increment();
    }
}
