package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

/**
 * Pregunta embebida dentro de un Exam.
 * Para TRUE_FALSE: options = ["Verdadero", "Falso"], correctOptionIndex = 0 ó 1.
 * Para MULTI_SELECT: options = 3 opciones, correctOptionIndexes = índices correctos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamQuestion {

    @Field("id")
    private String id;

    @Field("question_text")
    private String questionText;

    @Field("type")
    private QuestionType type;

    @Field("options")
    private List<String> options;

    /** Índice (0-based) de la opción correcta. Para TRUE_FALSE. */
    @Field("correct_option_index")
    private int correctOptionIndex;

    /** Índices correctos. Para MULTI_SELECT. */
    @Builder.Default
    @Field("correct_option_indexes")
    private List<Integer> correctOptionIndexes = new ArrayList<>();

    /** Retroalimentación mostrada al usuario después de responder. */
    @Field("explanation")
    private String explanation;

    @Builder.Default
    @Field("points")
    private int points = 1;
}
