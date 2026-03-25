package com.metrix.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Estadísticas agregadas de un examen — calculadas en tiempo real desde las submissions.
 */
@Data
@Builder
public class ExamStatsResponse {

    private String examId;
    private String examTitle;
    private long   totalSubmissions;
    private long   passedCount;
    private int    passRate;        // 0–100
    private double avgScore;
    private double minScore;
    private double maxScore;

    /** Distribución de puntajes en 4 rangos. */
    private ScoreRange range0_49;
    private ScoreRange range50_69;
    private ScoreRange range70_89;
    private ScoreRange range90_100;

    /** Tiempos en segundos. -1 si no hay datos. */
    private double avgTimeSecs;
    private int    minTimeSecs;
    private int    maxTimeSecs;

    /** Cuántas submissions tienen OPEN_TEXT pendiente de revisión. */
    private long pendingReviewCount;

    /** Preguntas ordenadas por tasa de fallo descendente. */
    private List<QuestionFailRate> questionFailRates;

    @Data
    @Builder
    public static class ScoreRange {
        private String label;       // "0–49", "50–69", etc.
        private long   count;
        private int    percentage;  // % del total
    }

    @Data
    @Builder
    public static class QuestionFailRate {
        private int    questionIndex;
        private String questionText;
        private long   failCount;
        private long   totalCount;
        private int    failRate;    // 0–100
    }
}
