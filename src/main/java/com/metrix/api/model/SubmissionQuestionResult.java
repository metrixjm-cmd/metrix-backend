package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * Desglose de resultado por pregunta, persistido en {@link ExamSubmission}.
 * Permite consultar el historial sin necesidad de recalcular.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionQuestionResult {

    @Field("question_text")
    private String questionText;

    @Field("type")
    private com.metrix.api.model.QuestionType type;

    @Field("options")
    private List<String> options;

    // ── MULTIPLE_CHOICE / TRUE_FALSE ──────────────────────────────────────
    @Field("selected_index")
    private int selectedIndex;

    @Field("correct_index")
    private int correctIndex;

    // ── MULTI_SELECT ──────────────────────────────────────────────────────
    @Field("selected_indexes")
    private List<Integer> selectedIndexes;

    @Field("correct_indexes")
    private List<Integer> correctIndexes;

    // ── OPEN_TEXT ─────────────────────────────────────────────────────────
    @Field("text_answer")
    private String textAnswer;

    @Field("accepted_keywords")
    private List<String> acceptedKeywords;

    /** true si OPEN_TEXT requiere revisión manual. */
    @Field("pending_review")
    private boolean pendingReview;

    // ── Comunes ───────────────────────────────────────────────────────────
    @Field("correct")
    private boolean correct;

    @Field("points_earned")
    private double pointsEarned;

    @Field("points_max")
    private int pointsMax;

    /** Retroalimentación mostrada al usuario. */
    @Field("explanation")
    private String explanation;

    /**
     * Override manual de revisión para OPEN_TEXT.
     * null = no revisado aún (auto-scored), true = aprobado, false = rechazado.
     */
    @Field("review_override")
    private Boolean reviewOverride;
}
