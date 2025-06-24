package com.henuka.imitations.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.beans.factory.annotation.Value;
import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.henuka.imitations.repository")
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String dataSourceUrl;

    @Value("${spring.datasource.username}")
    private String dataSourceUsername;

    @Value("${spring.datasource.password}")
    private String dataSourcePassword;

    @Value("${spring.jpa.properties.hibernate.dialect}")
    private String hibernateDialect;

    /**
     * Configure data source
     */
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dataSourceUrl);
        config.setUsername(dataSourceUsername);
        config.setPassword(dataSourcePassword);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);
        config.setMaxLifetime(1200000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return new HikariDataSource(config);
    }

    /**
     * Configure entity manager factory
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.henuka.imitations.model");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", hibernateDialect);
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.use_sql_comments", "true");
        properties.setProperty("hibernate.generate_statistics", "true");
        em.setJpaProperties(properties);

        return em;
    }

    /**
     * Configure transaction manager
     */
    @Bean
    public PlatformTransactionManager transactionManager(
            LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory.getObject());
        return transactionManager;
    }
}

/**
 * Database metrics service
 */
@org.springframework.stereotype.Service
class DatabaseMetricsService {
    
    private final javax.sql.DataSource dataSource;
    private final io.micrometer.core.instrument.MeterRegistry registry;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseMetricsService.class);

    public DatabaseMetricsService(javax.sql.DataSource dataSource,
                                io.micrometer.core.instrument.MeterRegistry registry) {
        this.dataSource = dataSource;
        this.registry = registry;
        monitorConnectionPool();
    }

    private void monitorConnectionPool() {
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
            com.zaxxer.hikari.HikariDataSource hikariDS = (com.zaxxer.hikari.HikariDataSource) dataSource;
            
            registry.gauge("hikari.connections.active", 
                hikariDS, com.zaxxer.hikari.HikariDataSource::getActiveConnections);
            
            registry.gauge("hikari.connections.idle", 
                hikariDS, com.zaxxer.hikari.HikariDataSource::getIdleConnections);
            
            registry.gauge("hikari.connections.total", 
                hikariDS, com.zaxxer.hikari.HikariDataSource::getHikariPoolMXBean,
                pool -> pool.getTotalConnections());
        }
    }

    public void recordQueryExecution(String query, long duration) {
        try {
            registry.timer("database.query.duration", 
                "query", query).record(java.time.Duration.ofMillis(duration));
        } catch (Exception e) {
            log.error("Failed to record query metrics", e);
        }
    }
}

/**
 * SQL execution listener
 */
@org.springframework.stereotype.Component
class SqlExecutionListener extends org.hibernate.resource.jdbc.spi.StatementInspector {
    
    private final DatabaseMetricsService metricsService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SqlExecutionListener.class);

    public SqlExecutionListener(DatabaseMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public String inspect(String sql) {
        try {
            long startTime = System.currentTimeMillis();
            String normalizedSql = normalizeSql(sql);
            long duration = System.currentTimeMillis() - startTime;
            
            metricsService.recordQueryExecution(normalizedSql, duration);
            return sql;
        } catch (Exception e) {
            log.error("Failed to inspect SQL", e);
            return sql;
        }
    }

    private String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ")
                 .replaceAll("\\d+", "?")
                 .trim();
    }
}

/**
 * Transaction aspect
 */
@org.springframework.stereotype.Component
@org.aspectj.lang.annotation.Aspect
class TransactionAspect {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TransactionAspect.class);

    public TransactionAspect(io.micrometer.core.instrument.MeterRegistry registry) {
        this.registry = registry;
    }

    @org.aspectj.lang.annotation.Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object measureTransaction(org.aspectj.lang.ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            registry.timer("transaction.duration", 
                "method", methodName).record(java.time.Duration.ofMillis(duration));
            
            return result;
        } catch (Exception e) {
            registry.counter("transaction.failure", 
                "method", methodName).increment();
            throw e;
        }
    }
}
