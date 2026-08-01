package com.metrix.api.event;

import com.metrix.api.event.DomainEvents.IncidentCreatedEvent;
import com.metrix.api.event.DomainEvents.IncidentStatusChangedEvent;
import com.metrix.api.event.DomainEvents.TaskCreatedEvent;
import com.metrix.api.event.DomainEvents.TaskStatusChangedEvent;
import com.metrix.api.repository.UserRepository;
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
    private final UserRepository userRepository;

    @EventListener
    public void onTaskCreated(TaskCreatedEvent event) {
        evictStoreCache(event.storeId());
        evictManagerTeamCache(event.assignedUserId());
    }

    @EventListener
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        evictStoreCache(event.storeId());
        evictManagerTeamCache(event.assignedUserId());
    }

    /**
     * kpiIncidents no tenía invalidación por evento — dependía solo del TTL de
     * 5 min de Caffeine, así que una incidencia recién creada o cerrada podía
     * tardar hasta 5 min en reflejarse en el panel de KPI mientras la lista de
     * incidencias (sin caché) ya la mostraba (2026-08-01, reporte del cliente:
     * "en incidencias no se refleja nada a pesar que sí aparecen").
     */
    @EventListener
    public void onIncidentCreated(IncidentCreatedEvent event) {
        evictIncidentCache(event.storeId());
    }

    @EventListener
    public void onIncidentStatusChanged(IncidentStatusChangedEvent event) {
        evictIncidentCache(event.storeId());
    }

    private void evictIncidentCache(String storeId) {
        var incidentCache = cacheManager.getCache("kpiIncidents");
        if (incidentCache == null) {
            return;
        }
        incidentCache.evict(storeId);
        incidentCache.evict("global");
        log.debug("[CACHE] Invalidated kpiIncidents for storeId={}", storeId);
    }

    private void evictStoreCache(String storeId) {
        var kpiCache = cacheManager.getCache("kpiSummary");
        if (kpiCache != null) {
            kpiCache.evict(storeId);
            kpiCache.evict("users-" + storeId);
        }
        var rankingCache = cacheManager.getCache("storeRanking");
        if (rankingCache != null) {
            rankingCache.clear();
        }
        log.debug("[CACHE] Invalidated kpiSummary for storeId={}", storeId);
    }

    private void evictManagerTeamCache(String assignedUserId) {
        if (assignedUserId == null || assignedUserId.isBlank()) {
            return;
        }
        var kpiCache = cacheManager.getCache("kpiSummary");
        if (kpiCache == null) {
            return;
        }
        userRepository.findById(assignedUserId).ifPresent(user -> {
            String managerId = user.getManagerOwnerId();
            if (managerId != null && !managerId.isBlank()) {
                kpiCache.evict("users-mgr-" + managerId);
                log.debug("[CACHE] Invalidated team KPI cache for managerId={}", managerId);
            }
        });
    }
}
