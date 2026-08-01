package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KPI #5: Cumplimiento por Turno.
 * Desglosa el On-Time Rate por turno (MATUTINO / VESPERTINO / NOCTURNO).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftBreakdownResponse {
    private String shift;       // MATUTINO | VESPERTINO | NOCTURNO
    private double onTimeRate;  // 0–100, -1 si totalClosed == 0 (sin datos, no 0%)
    private int totalClosed;    // COMPLETED + FAILED en este turno
    private int onTimeCount;    // closed con onTime=true
    /**
     * Tareas totales asignadas a este turno (cualquier estado). Distingue
     * "turno sin ninguna tarea" (totalTasks == 0) de "turno con tareas que
     * aún no cierran" (totalTasks > 0, totalClosed == 0) — antes ambos casos
     * se veían iguales en la UI porque el turno vacío ni se enviaba (2026-08-01).
     */
    private int totalTasks;
}
