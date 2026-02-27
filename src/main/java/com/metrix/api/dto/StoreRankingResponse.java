package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KPI #6: Ranking Inter-Sucursal.
 * Un entry por sucursal, ordenado por IGEO descendente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreRankingResponse {
    private int    rank;           // 1-based
    private String storeId;        // display name — no existe colección stores aún
    private double igeo;           // KPI #10 composite
    private double onTimeRate;     // KPI #1
    private double reworkRate;     // KPI #3
    private int    totalTasks;
    private int    completedTasks;
    private int    failedTasks;
}
