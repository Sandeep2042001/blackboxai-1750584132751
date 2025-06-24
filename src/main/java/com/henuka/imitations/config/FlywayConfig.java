package com.henuka.imitations.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    /**
     * Configure Flyway
     */
    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource, Environment env) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .cleanDisabled(!"dev".equals(env.getActiveProfiles()[0]))
                .placeholderReplacement(true)
                .placeholderPrefix("${")
                .placeholderSuffix("}")
                .load();
    }
}

/**
 * Migration version tracker
 */
@org.springframework.stereotype.Component
class MigrationVersionTracker {
    
    private final DataSource dataSource;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MigrationVersionTracker.class);

    public MigrationVersionTracker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Get current schema version
     */
    public String getCurrentVersion() {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(
                     "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")) {
            
            java.sql.ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("version") : "0";
        } catch (Exception e) {
            log.error("Failed to get current schema version", e);
            return "unknown";
        }
    }

    /**
     * Get migration history
     */
    public java.util.List<MigrationInfo> getMigrationHistory() {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM flyway_schema_history ORDER BY installed_rank")) {
            
            java.sql.ResultSet rs = stmt.executeQuery();
            java.util.List<MigrationInfo> history = new java.util.ArrayList<>();
            
            while (rs.next()) {
                history.add(new MigrationInfo(
                    rs.getString("version"),
                    rs.getString("description"),
                    rs.getTimestamp("installed_on"),
                    rs.getString("success").equals("1")
                ));
            }
            
            return history;
        } catch (Exception e) {
            log.error("Failed to get migration history", e);
            return java.util.Collections.emptyList();
        }
    }
}

/**
 * Migration information
 */
class MigrationInfo {
    private final String version;
    private final String description;
    private final java.util.Date installedOn;
    private final boolean success;

    public MigrationInfo(String version, String description, 
                        java.util.Date installedOn, boolean success) {
        this.version = version;
        this.description = description;
        this.installedOn = installedOn;
        this.success = success;
    }

    // Getters
    public String getVersion() { return version; }
    public String getDescription() { return description; }
    public java.util.Date getInstalledOn() { return installedOn; }
    public boolean isSuccess() { return success; }
}

/**
 * Migration health indicator
 */
@org.springframework.stereotype.Component
class MigrationHealthIndicator implements org.springframework.boot.actuate.health.HealthIndicator {
    
    private final MigrationVersionTracker versionTracker;

    public MigrationHealthIndicator(MigrationVersionTracker versionTracker) {
        this.versionTracker = versionTracker;
    }

    @Override
    public org.springframework.boot.actuate.health.Health health() {
        try {
            String currentVersion = versionTracker.getCurrentVersion();
            java.util.List<MigrationInfo> history = versionTracker.getMigrationHistory();
            boolean allSuccessful = history.stream().allMatch(MigrationInfo::isSuccess);

            return allSuccessful
                    ? org.springframework.boot.actuate.health.Health.up()
                            .withDetail("currentVersion", currentVersion)
                            .withDetail("migrationCount", history.size())
                            .build()
                    : org.springframework.boot.actuate.health.Health.down()
                            .withDetail("currentVersion", currentVersion)
                            .withDetail("failedMigrations", 
                                history.stream()
                                    .filter(m -> !m.isSuccess())
                                    .map(MigrationInfo::getVersion)
                                    .collect(java.util.stream.Collectors.toList()))
                            .build();
        } catch (Exception e) {
            return org.springframework.boot.actuate.health.Health.down()
                    .withException(e)
                    .build();
        }
    }
}

/**
 * Migration event listener
 */
@org.springframework.stereotype.Component
class MigrationEventListener {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MigrationEventListener.class);

    @org.springframework.context.event.EventListener
    public void handleMigrationEvent(org.flywaydb.core.api.event.Event event) {
        if (event instanceof org.flywaydb.core.api.event.MigrationInfoEvent) {
            log.info("Migration info loaded");
        } else if (event instanceof org.flywaydb.core.api.event.MigrationStartedEvent) {
            log.info("Migration started");
        } else if (event instanceof org.flywaydb.core.api.event.MigrationCompletedEvent) {
            org.flywaydb.core.api.event.MigrationCompletedEvent completedEvent = 
                (org.flywaydb.core.api.event.MigrationCompletedEvent) event;
            log.info("Migration completed: {}", completedEvent.getMigrationInfo().getDescription());
        }
    }
}
