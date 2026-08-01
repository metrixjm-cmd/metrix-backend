package com.metrix.api.event;

import com.metrix.api.event.DomainEvents.IncidentCreatedEvent;
import com.metrix.api.event.DomainEvents.IncidentStatusChangedEvent;
import com.metrix.api.model.IncidentStatus;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.mockito.Mockito.*;

/**
 * kpiIncidents dependía solo del TTL de 5 min de Caffeine, sin invalidación
 * por evento (2026-08-01, reporte del cliente sobre el panel de incidencias).
 * Cubre que crear o cambiar el estado de una incidencia evict tanto la
 * entrada de su sucursal como la global.
 */
@ExtendWith(MockitoExtension.class)
class KpiCacheInvalidatorTest {

    @Mock private CacheManager   cacheManager;
    @Mock private Cache          kpiIncidentsCache;
    @Mock private UserRepository userRepository;

    private KpiCacheInvalidator invalidator;

    @BeforeEach
    void setUp() {
        invalidator = new KpiCacheInvalidator(cacheManager, userRepository);
        when(cacheManager.getCache("kpiIncidents")).thenReturn(kpiIncidentsCache);
    }

    @Test
    void onIncidentCreated_evictaSucursalYGlobal() {
        invalidator.onIncidentCreated(
                new IncidentCreatedEvent("inc1", "store-1", "user1", "Fuga", "Rep", "MATUTINO", "ALTA"));

        verify(kpiIncidentsCache).evict("store-1");
        verify(kpiIncidentsCache).evict("global");
    }

    @Test
    void onIncidentStatusChanged_evictaSucursalYGlobal() {
        invalidator.onIncidentStatusChanged(new IncidentStatusChangedEvent(
                "inc1", IncidentStatus.ABIERTA, IncidentStatus.CERRADA, "store-2", "user1", "Fuga", null));

        verify(kpiIncidentsCache).evict("store-2");
        verify(kpiIncidentsCache).evict("global");
    }

    @Test
    void onIncidentCreated_noRompeSiElCacheNoExiste() {
        when(cacheManager.getCache("kpiIncidents")).thenReturn(null);

        invalidator.onIncidentCreated(
                new IncidentCreatedEvent("inc1", "store-1", "user1", "Fuga", "Rep", "MATUTINO", "ALTA"));
        // No exception esperado — verificado implícitamente por no fallar el test.
    }
}
