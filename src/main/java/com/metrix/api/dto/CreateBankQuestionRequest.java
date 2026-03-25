package com.metrix.api.dto;

import com.metrix.api.model.QuestionDifficulty;
import com.metrix.api.model.QuestionType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateBankQuestionRequest {

    @NotBlank
    @Size(max = 500)
    private String questionText;

    @NotNull
    private QuestionType type;

    /** Opciones de respuesta. Requerido para MULTIPLE_CHOICE, MULTI_SELECT, TRUE_FALSE. */
    private List<String> options = new ArrayList<>();

    /** Índice correcto. Para MULTIPLE_CHOICE y TRUE_FALSE. */
    private int correctOptionIndex;

    /** Índices correctos. Para MULTI_SELECT. */
    private List<Integer> correctOptionIndexes = new ArrayList<>();

    /** Palabras clave aceptadas. Para OPEN_TEXT. */
    private List<String> acceptedKeywords = new ArrayList<>();

    /** Retroalimentación post-respuesta. */
    private String explanation;

    @Min(1) @Max(10)
    private int points = 1;

    private String category;

    private QuestionDifficulty difficulty;

    @Size(max = 10)
    private List<String> tags = new ArrayList<>();

    /** Null = pregunta global. Si se provee, solo visible para esa sucursal. */
    private String storeId;
}
