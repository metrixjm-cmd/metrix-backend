package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * KPIs agregados de exámenes de una sucursal (Sprint 19).
 * <p>
 * Agrega TODAS las submissions de la sucursal — a diferencia de
 * {@code ExamStatsResponse} que es por examen individual. Alimenta el tab
 * "Exámenes" del panel /kpi: gauge de aprobación, distribución de puntajes
 * y ranking por examen.
 * <p>
 * Convención de sentinel: valores {@code -1.0} indican "sin datos suficientes".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamKpiResponse {

    private String storeId;
    private long   totalExams;
    private long   totalSubmissions;

    // ── Gauge / métricas globales ────────────────────────────────────────────
    /** % de submissions aprobadas. -1.0 si no hay submissions. */
    private double passRate;
    /** Puntaje promedio (0–100). -1.0 si no hay submissions. */
    private double avgScore;
    private double minScore;
    private double maxScore;
    /** Tiempo promedio en segundos. -1.0 si sin datos. */
    private double avgTimeSecs;

    // ── Distribución de puntajes (0–49, 50–69, 70–89, 90–100) ────────────────
    private List<LabelCount> scoreDistribution;

    // ── Ranking por examen ───────────────────────────────────────────────────
    private List<ExamRow> perExam;

    // ── Desglose por colaborador ──────────────────────────────────────────────
    private List<ExamUserRow> perUser;

    /** Fila de la tabla ranking por examen. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExamRow {
        private String examId;
        private String examTitle;
        private long   submissions;
        private int    passRate;   // 0–100
        private double avgScore;   // 0–100
    }

    /** Fila del desglose por colaborador (todas sus submissions en la sucursal). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExamUserRow {
        private String userId;
        private String userName;
        private long   submissions;
        private long   passed;
        private int    passRate;   // 0–100
        private double avgScore;   // 0–100
    }
}
