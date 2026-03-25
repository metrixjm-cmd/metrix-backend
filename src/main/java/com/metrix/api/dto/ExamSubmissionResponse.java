package com.metrix.api.dto;

import com.metrix.api.model.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ExamSubmissionResponse {

    private String id;
    private String examId;
    private String examTitle;
    private String userName;
    private String userNumero;
    private String storeId;
    private double score;
    private boolean passed;
    private int passingScore;
    private Integer timeTakenSeconds;
    private Instant submittedAt;

    /** true si alguna pregunta OPEN_TEXT quedó sin confirmar automáticamente. */
    private boolean hasPendingReview;

    /** true si ADMIN/GERENTE ya revisó las respuestas OPEN_TEXT pendientes. */
    private boolean reviewed;

    /** Flags de actividad sospechosa (ej. RESPUESTA_MUY_RAPIDA). */
    private List<String> fraudFlags;

    /** Desglose pregunta a pregunta. */
    private List<QuestionResult> questionResults;

    @Data
    @Builder
    public static class QuestionResult {
        private String       questionText;
        private QuestionType type;
        private List<String> options;

        // MULTIPLE_CHOICE / TRUE_FALSE
        private int selectedIndex;
        private int correctIndex;

        // MULTI_SELECT
        private List<Integer> selectedIndexes;
        private List<Integer> correctIndexes;

        // OPEN_TEXT
        private String       textAnswer;
        private List<String> acceptedKeywords;
        private boolean      pendingReview;

        // Comunes
        private boolean correct;
        private double  pointsEarned;
        private int     pointsMax;
        private String  explanation;
    }
}
