package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Datos ensamblados para el reporte de cierre diario de una sucursal.
 * <p>
 * Agrupa KPIs, tareas y ranking de colaboradores del día solicitado.
 * Sirve como base para generar tanto el preview JSON como los archivos
 * PDF y Excel descargables.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyReportResponse {

    private String storeId;
    private LocalDate reportDate;

    /** KPIs calculados solo con las tareas del día solicitado. */
    private KpiSummaryResponse kpiSummary;

    /** Tareas de la sucursal creadas en el día solicitado. */
    private List<TaskResponse> tasks;

    /** Ranking de colaboradores de la sucursal por IGEO individual. */
    private List<UserResponsibilityResponse> userRanking;

    /** KPI #9: velocidad de corrección calculada con tareas del día. */
    private CorrectionSpeedResponse correctionSpeed;

    private int totalAssigned;
    private int totalCompleted;
    private int totalFailed;
    private int totalPending;
}
