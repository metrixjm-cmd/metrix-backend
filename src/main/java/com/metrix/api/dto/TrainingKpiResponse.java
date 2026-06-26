package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * KPIs agregados de capacitaciones de una sucursal (Sprint 10).
 * <p>
 * Alimenta el tab "Capacitaciones" del panel /kpi: gauge de completación,
 * donut por estado del ciclo de vida, tasa de aprobación y promedio de calificación.
 * <p>
 * Convención de sentinel: valores {@code -1.0} indican "sin datos suficientes".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingKpiResponse {

    private String storeId;
    private long   total;

    // ── Donut por estado ─────────────────────────────────────────────────────
    private long programadas;
    private long enCurso;
    private long completadas;
    private long noCompletadas;

    // ── Gauges / métricas ────────────────────────────────────────────────────
    /** % completadas sobre el total. -1.0 si no hay capacitaciones. */
    private double completionRate;
    /** % a tiempo entre las completadas. -1.0 si no hay completadas. */
    private double onTimeRate;
    /** % aprobadas entre las que ya tienen veredicto (passed != null). -1.0 si ninguna. */
    private double passRate;
    /** Calificación promedio (0.0–10.0) de las que tienen grade. -1.0 si ninguna. */
    private double avgGrade;
    /** Porcentaje de avance promedio (0–100) sobre todas. */
    private double avgProgress;

    /** Capacitaciones PROGRAMADA/EN_CURSO cuya fecha límite ya venció. */
    private long overduePending;

    // ── Distribución ─────────────────────────────────────────────────────────
    /** Conteo por categoría, ordenado descendente por conteo. */
    private List<LabelCount> byCategory;
}
