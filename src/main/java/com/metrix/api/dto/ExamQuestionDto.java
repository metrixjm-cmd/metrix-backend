package com.metrix.api.dto;

import com.metrix.api.model.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ExamQuestionDto {

    @NotBlank
    private String questionText;

    @NotNull
    private QuestionType type;

    /**
     * Opciones de respuesta. Requerido para MC/TF/MULTI_SELECT (2-6 items).
     * Para OPEN_TEXT puede ser null o vacío.
     */
    private List<String> options;

    /** Índice de la opción correcta. Para MULTIPLE_CHOICE y TRUE_FALSE. */
    private int correctOptionIndex;

    /** Índices correctos. Para MULTI_SELECT. */
    private List<Integer> correctOptionIndexes;

    /** Palabras clave aceptadas. Para OPEN_TEXT. */
    private List<String> acceptedKeywords;

    /** Retroalimentación post-respuesta (opcional). */
    private String explanation;

    private int points = 1;
}
