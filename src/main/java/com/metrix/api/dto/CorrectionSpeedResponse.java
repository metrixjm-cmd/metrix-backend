package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KPI #9 — Velocidad de Corrección.
 * <p>
 * Tiempo promedio para re-ejecutar tareas fallidas, medido desde la
 * transición FAILED hasta la siguiente COMPLETED tras un ciclo de rework.
 * <p>
 * Si no hay tareas con rework completado, {@code avgCorrectionMinutes} = -1.0.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionSpeedResponse {

    private String storeId;

    /** Número de tareas que tienen al menos un ciclo de rework con datos. */
    private int reworkedTasks;

    /** Tiempo promedio de corrección en minutos. -1.0 si sin datos. */
    private double avgCorrectionMinutes;

    /** Tiempo mínimo de corrección en minutos. -1.0 si sin datos. */
    private double minCorrectionMinutes;

    /** Tiempo máximo de corrección en minutos. -1.0 si sin datos. */
    private double maxCorrectionMinutes;
}
