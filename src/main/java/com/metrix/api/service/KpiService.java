package com.metrix.api.service;

import com.metrix.api.dto.CorrectionSpeedResponse;
import com.metrix.api.dto.KpiSummaryResponse;
import com.metrix.api.dto.StoreRankingResponse;
import com.metrix.api.dto.UserResponsibilityResponse;

import java.util.List;

/**
 * Contrato de cálculo de KPIs del sistema METRIX.
 * <p>
 * Sprint 7 implementa KPIs #1–#6, #8, #10.
 * KPI #7 (Responsabilidad Individual por colaborador) y
 * KPI #9 (Velocidad Corrección — requiere historial de transiciones)
 * se posponen al Sprint 8.
 */
public interface KpiService {

    /**
     * KPIs de una sucursal: agrega todas las tareas activas del storeId.
     * Acceso: ADMIN, GERENTE.
     */
    KpiSummaryResponse getStoreSummary(String storeId);

    /**
     * KPIs del usuario autenticado: agrega todas sus tareas activas.
     * Acceso: cualquier rol autenticado.
     */
    KpiSummaryResponse getUserSummary(String userId);

    /**
     * Ranking inter-sucursal ordenado por IGEO descendente.
     * Acceso: ADMIN.
     */
    List<StoreRankingResponse> getStoreRanking();

    /**
     * KPI #7 — Responsabilidad Individual: ranking de colaboradores de una sucursal
     * con sus KPIs personales calculados.
     * Acceso: ADMIN, GERENTE.
     */
    List<UserResponsibilityResponse> getUsersResponsibility(String storeId);

    /**
     * KPI #9 — Velocidad de Corrección: tiempo promedio para re-ejecutar tareas fallidas.
     * Requiere el historial de {@link com.metrix.api.model.StatusTransition} en Task.
     * Acceso: ADMIN, GERENTE.
     */
    CorrectionSpeedResponse getCorrectionSpeed(String storeId);
}
