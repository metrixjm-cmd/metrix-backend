package com.metrix.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache SIEMPRE en Caffeine (in-memory).
 * <p>
 * Decisión de arquitectura (2026-03-14):
 * Con Cloud Run limitado a 1 instancia, cache distribuido no aporta valor.
 * Caffeine es ~100x más rápido que Redis (no hay round-trip de red)
 * y $0/mes en costos.
 * <p>
 * Caches registrados:
 * <ul>
 *   <li>{@code kpiSummary}        — TTL 5 min, max 100 entries</li>
 *   <li>{@code storeRanking}      — TTL 5 min, max 50 entries</li>
 *   <li>{@code leaderboard}       — TTL 10 min, max 50 entries</li>
 *   <li>{@code templateSummaries} — TTL 5 min, max 50 entries</li>
 *   <li>{@code materialTags}      — TTL 10 min, max 1 entry</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager caffeineCacheManager() {
        log.info("[CacheConfig] Caffeine in-memory cache — zero-cost mode");

        CaffeineCacheManager manager = new CaffeineCacheManager(
                "kpiSummary", "storeRanking", "leaderboard",
                "templateSummaries", "materialTags", "questionBankTags",
                "examTemplateSummaries");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(200)
                .recordStats());
        return manager;
    }
}
