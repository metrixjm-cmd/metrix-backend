package com.metrix.api.event;

import com.metrix.api.event.DomainEvents.TaskCreatedEvent;
import com.metrix.api.event.DomainEvents.TaskStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Surgical KPI cache invalidation driven by domain events.
 * <p>
 * Instead of evicting ALL cache entries on any task change (old approach),
 * this listener only invalidates the cache entry for the affected store.
 * With 10 stores, this means 90% fewer cache misses after a single task update.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KpiCacheInvalidator {

    private final CacheManager cacheManager;

    @EventListener
    public void onTaskCreated(TaskCreatedEvent event) {
        evictStoreCache(event.storeId());
    }

    @EventListener
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        evictStoreCache(event.storeId());
    }

    private void evictStoreCache(String storeId) {
        var kpiCache = cacheManager.getCache("kpiSummary");
        if (kpiCache != null) {
            kpiCache.evict(storeId);
            kpiCache.evict("users-" + storeId);  // getUsersResponsibility cache key
        }
        var rankingCache = cacheManager.getCache("storeRanking");
        if (rankingCache != null) {
            rankingCache.clear();  // Ranking is global — must be fully invalidated
        }
        log.debug("[CACHE] Invalidated kpiSummary for storeId={}", storeId);
    }
}
