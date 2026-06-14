package com.stockdashboard.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String STOCK_METRICS_CACHE = "stockMetrics";
    public static final String UNIVERSE_CODES_CACHE = "universeCodes";
    public static final String UNIVERSE_METRICS_CACHE = "universeMetrics";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(1000));
        cacheManager.setCacheNames(List.of(STOCK_METRICS_CACHE));
        cacheManager.registerCustomCache(UNIVERSE_CODES_CACHE,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6)).maximumSize(1).build());
        cacheManager.registerCustomCache(UNIVERSE_METRICS_CACHE,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).maximumSize(1).build());
        return cacheManager;
    }
}
