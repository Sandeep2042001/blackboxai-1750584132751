package com.henuka.imitations.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure cache manager
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        java.util.Map<String, RedisCacheConfiguration> configs = new java.util.HashMap<>();
        configs.put("products", defaultConfig.entryTtl(Duration.ofHours(1)));
        configs.put("categories", defaultConfig.entryTtl(Duration.ofHours(2)));
        configs.put("users", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .build();
    }
}

/**
 * Cache service
 */
@org.springframework.stereotype.Service
class CacheService {
    
    private final CacheManager cacheManager;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CacheService.class);

    public CacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Clear specific cache
     */
    public void clearCache(String cacheName) {
        try {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("Cache cleared: {}", cacheName);
            }
        } catch (Exception e) {
            log.error("Failed to clear cache: {}", cacheName, e);
            throw new CacheException("Failed to clear cache", e);
        }
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        try {
            cacheManager.getCacheNames()
                    .forEach(this::clearCache);
            log.info("All caches cleared");
        } catch (Exception e) {
            log.error("Failed to clear all caches", e);
            throw new CacheException("Failed to clear all caches", e);
        }
    }

    /**
     * Get cache statistics
     */
    public java.util.Map<String, CacheStats> getCacheStats() {
        java.util.Map<String, CacheStats> stats = new java.util.HashMap<>();
        
        try {
            for (String cacheName : cacheManager.getCacheNames()) {
                org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                if (cache instanceof org.springframework.data.redis.cache.RedisCache) {
                    org.springframework.data.redis.cache.RedisCache redisCache = 
                        (org.springframework.data.redis.cache.RedisCache) cache;
                    
                    stats.put(cacheName, new CacheStats(
                        cacheName,
                        redisCache.getNativeCache().keys("*").size(),
                        getCacheTtl(cacheName)
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Failed to get cache stats", e);
        }
        
        return stats;
    }

    private Duration getCacheTtl(String cacheName) {
        RedisCacheConfiguration config = ((RedisCacheManager) cacheManager)
                .getCacheConfigurations()
                .get(cacheName);
        return config != null ? config.getTtl() : Duration.ZERO;
    }
}

/**
 * Cache exception
 */
class CacheException extends RuntimeException {
    
    public CacheException(String message) {
        super(message);
    }

    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Cache statistics
 */
class CacheStats {
    private final String name;
    private final long size;
    private final Duration ttl;

    public CacheStats(String name, long size, Duration ttl) {
        this.name = name;
        this.size = size;
        this.ttl = ttl;
    }

    // Getters
    public String getName() { return name; }
    public long getSize() { return size; }
    public Duration getTtl() { return ttl; }
}

/**
 * Cache metrics
 */
@org.springframework.stereotype.Component
class CacheMetrics {
    
    private final io.micrometer.core.instrument.MeterRegistry registry;
    private final CacheService cacheService;

    public CacheMetrics(io.micrometer.core.instrument.MeterRegistry registry,
                       CacheService cacheService) {
        this.registry = registry;
        this.cacheService = cacheService;
        recordMetrics();
    }

    private void recordMetrics() {
        registry.gauge("cache.size", 
            cacheService.getCacheStats().values(), 
            stats -> stats.stream()
                    .mapToDouble(CacheStats::getSize)
                    .sum());

        cacheService.getCacheStats().forEach((name, stats) -> 
            registry.gauge("cache.size.by.name", 
                java.util.Collections.singletonList(io.micrometer.core.instrument.Tag.of("cache", name)),
                stats,
                CacheStats::getSize));
    }

    public void recordCacheHit(String cacheName) {
        registry.counter("cache.hits", "cache", cacheName).increment();
    }

    public void recordCacheMiss(String cacheName) {
        registry.counter("cache.misses", "cache", cacheName).increment();
    }
}
