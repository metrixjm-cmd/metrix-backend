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
 * Para MULTIPLE_CHOICE: 4 opciones en {@code options}, índice correcto en {@code correctOptionIndex}.
 * Para TRUE_FALSE: options = ["Verdadero", "Falso"], correctOptionIndex = 0 ó 1.
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

    /** Índice (0-based) de la opción correcta. Para MULTIPLE_CHOICE y TRUE_FALSE. */
    @Field("correct_option_index")
    private int correctOptionIndex;

    /** Índices correctos. Para MULTI_SELECT (puede haber N correctas). */
    @Builder.Default
    @Field("correct_option_indexes")
    private List<Integer> correctOptionIndexes = new ArrayList<>();

    /** Palabras clave aceptadas. Para OPEN_TEXT (matching insensible a mayúsculas). */
    @Builder.Default
    @Field("accepted_keywords")
    private List<String> acceptedKeywords = new ArrayList<>();

    /** Retroalimentación mostrada al usuario después de responder. */
    @Field("explanation")
    private String explanation;

    @Builder.Default
    @Field("points")
    private int points = 1;
}
