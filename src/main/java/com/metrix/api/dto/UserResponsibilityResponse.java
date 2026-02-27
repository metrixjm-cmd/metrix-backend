package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KPI #7 — Responsabilidad Individual.
 * <p>
 * Ranking de colaboradores dentro de una sucursal con sus KPIs personales.
 * Ordenados por IGEO individual descendente, con rank 1-based.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponsibilityResponse {

    private String userId;
    private String nombre;
    private String position;
    private String turno;

    private int totalTasks;
    private int completedTasks;
    private int failedTasks;

    /** KPI #1 individual: % tareas completadas a tiempo. -1.0 si sin datos. */
    private double onTimeRate;

    /** KPI #3 individual: % tareas con al menos 1 re-trabajo. */
    private double reworkRate;

    /** KPI #4 individual: tiempo promedio de ejecución en minutos. -1.0 si sin datos. */
    private double avgExecMinutes;

    /** KPI #10 individual: IGEO calculado con los KPIs personales. */
    private double igeo;

    /** Posición 1-based dentro del ranking de la sucursal. */
    private int rank;
}
