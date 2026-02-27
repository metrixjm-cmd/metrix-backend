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
    private double onTimeRate;  // 0–100
    private int totalClosed;    // COMPLETED + FAILED en este turno
    private int onTimeCount;    // closed con onTime=true
}
