package com.metrix.api.dto;

import com.metrix.api.model.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ExamQuestionDto {

    @NotBlank
    private String questionText;

    @NotNull
    private QuestionType type;

    /** Opciones de respuesta. TRUE_FALSE: 2 fijas. MULTI_SELECT: 3. */
    private List<String> options;

    /** Índice de la opción correcta. Para TRUE_FALSE. */
    private int correctOptionIndex;

    /** Índices correctos. Para MULTI_SELECT. */
    private List<Integer> correctOptionIndexes;

    /** Retroalimentación post-respuesta (opcional). */
    private String explanation;

    private int points = 1;
}
