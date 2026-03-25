package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta unificada por pregunta — soporta los 4 tipos de QuestionType.
 * <p>
 * Solo uno de los tres campos debe estar presente por pregunta:
 * <ul>
 *   <li>{@code selectedIndex} → MULTIPLE_CHOICE, TRUE_FALSE</li>
 *   <li>{@code selectedIndexes} → MULTI_SELECT</li>
 *   <li>{@code textAnswer} → OPEN_TEXT</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAnswer {

    /** Índice 0-based de la opción elegida. -1 = sin respuesta. */
    private Integer selectedIndex;

    /** Índices de opciones elegidas (MULTI_SELECT). Vacío = sin respuesta. */
    private List<Integer> selectedIndexes;

    /** Texto libre del usuario (OPEN_TEXT). */
    private String textAnswer;
}
