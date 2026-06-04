package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * Desglose de resultado por pregunta, persistido en {@link ExamSubmission}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionQuestionResult {

    @Field("question_text")
    private String questionText;

    @Field("type")
    private QuestionType type;

    @Field("options")
    private List<String> options;

    // ── TRUE_FALSE ────────────────────────────────────────────────────────
    @Field("selected_index")
    private int selectedIndex;

    @Field("correct_index")
    private int correctIndex;

    // ── MULTI_SELECT ──────────────────────────────────────────────────────
    @Field("selected_indexes")
    private List<Integer> selectedIndexes;

    @Field("correct_indexes")
    private List<Integer> correctIndexes;

    // ── Comunes ───────────────────────────────────────────────────────────
    @Field("correct")
    private boolean correct;

    @Field("points_earned")
    private double pointsEarned;

    @Field("points_max")
    private int pointsMax;

    @Field("explanation")
    private String explanation;
}
