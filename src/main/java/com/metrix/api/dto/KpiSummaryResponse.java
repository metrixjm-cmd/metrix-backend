package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta unificada de KPIs para una sucursal o usuario.
 * <p>
 * Sentinel -1.0 indica "sin datos suficientes" para rates que requieren
 * al menos una tarea cerrada. El frontend muestra "S/D" en ese caso.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiSummaryResponse {
    // Contexto
    private String context;    // "STORE" | "USER"
    private String contextId;  // storeId o userId

    // KPI #1 — On-Time Rate
    private double onTimeRate;           // -1.0 = sin datos

    // KPI #2 — Delegación Efectiva
    private double delegacionEfectiva;   // -1.0 = sin datos

    // KPI #3 — Tasa de Re-trabajo
    private double reworkRate;           // 0.0 si no hay tareas

    // KPI #4 — Tiempo Promedio de Ejecución (minutos)
    private double avgExecutionMinutes;  // -1.0 = sin datos

    // KPI #5 — Cumplimiento por Turno
    private List<ShiftBreakdownResponse> shiftBreakdown;

    // KPI #8 — Críticas No Ejecutadas (count absoluto)
    private int criticalPending;

    // KPI #10 — IGEO (Índice Global de Ejecución Operacional)
    private double igeo;                 // -1.0 = sin datos

    // Pipeline counts (para el dashboard)
    private long pipelinePending;
    private long pipelineInProgress;
    private long pipelineCompleted;
    private long pipelineFailed;

    // Sparklines (últimas 10 tareas cerradas, orden ASC por createdAt)
    private List<Integer> sparklineOnTime;  // 100 = onTime, 0 = no
    private List<Double>  sparklineIgeo;    // IGEO rolling per task

    // Calidad promedio
    private double avgQualityRating;        // 1.0–5.0 o -1.0
}
