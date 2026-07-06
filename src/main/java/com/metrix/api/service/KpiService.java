package com.metrix.api.service;

import com.metrix.api.dto.CorrectionSpeedResponse;
import com.metrix.api.dto.ExamKpiResponse;
import com.metrix.api.dto.IgeoAnalyticsResponse;
import com.metrix.api.dto.IncidentKpiResponse;
import com.metrix.api.dto.KpiSummaryResponse;
import com.metrix.api.dto.StoreRankingResponse;
import com.metrix.api.dto.TrainingKpiResponse;
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
     * KPIs globales: agrega TODAS las tareas activas del sistema (todas las sucursales).
     * Vista principal del dashboard del ADMIN, que no está ligado a una sucursal.
     * Acceso: ADMIN.
     */
    KpiSummaryResponse getGlobalSummary();

    /**
     * KPI #7 global — Responsabilidad Individual de TODOS los colaboradores activos
     * del sistema, sin filtrar por sucursal. Para el dashboard del ADMIN.
     * Acceso: ADMIN.
     */
    List<UserResponsibilityResponse> getUsersResponsibilityGlobal();

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
     * KPI #7 — Responsabilidad Individual de los ejecutadores bajo un gerente.
     * Acceso: GERENTE (su propio equipo).
     */
    List<UserResponsibilityResponse> getUsersResponsibilityForManager(String storeId, String managerId);

    /**
     * KPI #9 — Velocidad de Corrección: tiempo promedio para re-ejecutar tareas fallidas.
     * Requiere el historial de {@link com.metrix.api.model.StatusTransition} en Task.
     * Acceso: ADMIN, GERENTE.
     */
    CorrectionSpeedResponse getCorrectionSpeed(String storeId);

    /**
     * KPI #10 — IGEO analítico (Sprint 17): delega al microservicio Python
     * {@code analytics-service} el cálculo del IGEO compuesto de 4 pilares
     * (Cumplimiento × 0.40 + Tiempo × 0.25 + Calidad × 0.20 + Consistencia × 0.15).
     * <p>
     * A diferencia de {@code getStoreSummary()} que calcula el IGEO en memoria con
     * la fórmula simplificada del Sprint 7, este método consume el KPI completo
     * calculado por pandas sobre toda la colección {@code tasks}.
     * <p>
     * Acceso: ADMIN, GERENTE.
     *
     * @throws org.springframework.web.client.RestClientException si el analytics-service
     *         no está disponible en {@code metrix.analytics.url}.
     */
    IgeoAnalyticsResponse getGlobalIgeoAnalytics();

    /**
     * KPIs agregados de incidencias de una sucursal: tasa de resolución,
     * desglose por estado/severidad/categoría, críticas abiertas y tiempo medio
     * de resolución.
     * Acceso: ADMIN, GERENTE.
     */
    IncidentKpiResponse getIncidentKpis(String storeId);

    /**
     * KPIs agregados de capacitaciones de una sucursal: completación, aprobación,
     * calificación promedio, desglose por estado y vencidas pendientes.
     * Acceso: ADMIN, GERENTE.
     */
    TrainingKpiResponse getTrainingKpis(String storeId);

    /**
     * KPIs agregados de exámenes de una sucursal: tasa de aprobación global,
     * distribución de puntajes y ranking por examen, agregando todas las
     * submissions de la sucursal.
     * Acceso: ADMIN, GERENTE.
     */
    ExamKpiResponse getExamKpis(String storeId);
}
