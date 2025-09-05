package com.maxx_global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Arrays;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();

        cacheManager.setCaches(Arrays.asList(
                new CaffeineCache("dashboardOverview",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofMinutes(5))
                                .maximumSize(500)
                                .build()),

                new CaffeineCache("systemStatistics",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofMinutes(10))
                                .maximumSize(500)
                                .build()),

                new CaffeineCache("monthlyOrderTrend",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofMinutes(30))
                                .maximumSize(500)
                                .build()),

                new CaffeineCache("dailyOrderVolume",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofMinutes(15))
                                .maximumSize(500)
                                .build()),

                new CaffeineCache("orderStatusDistribution",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofMinutes(15))
                                .maximumSize(500)
                                .build()),

                new CaffeineCache("topDealers",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofMinutes(30))
                                .maximumSize(500)
                                .build()),

                new CaffeineCache("revenueTimeline",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofMinutes(30))
                                .maximumSize(500)
                                .build()),

                new CaffeineCache("averageOrderValueTrend",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofMinutes(30))
                                .maximumSize(500)
                                .build()),

                new CaffeineCache("dealerPerformance",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofHours(1))
                                .maximumSize(500)
                                .build()),

                new CaffeineCache("dealerOrderFrequency",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofHours(1))
                                .maximumSize(500)
                                .build()),

                new CaffeineCache("topProducts",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofMinutes(30))
                                .maximumSize(500)
                                .build()),

                new CaffeineCache("discountEffectiveness",
                        Caffeine.newBuilder()
                                .expireAfterWrite(Duration.ofHours(1))
                                .maximumSize(500)
                                .build())
        ));

        return cacheManager;
    }
}
