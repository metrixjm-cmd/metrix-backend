package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta por pregunta al enviar un examen.
 * <ul>
 *   <li>{@code selectedIndex} → TRUE_FALSE (0 = Verdadero, 1 = Falso)</li>
 *   <li>{@code selectedIndexes} → MULTI_SELECT</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAnswer {

    /** Índice 0-based de la opción elegida. Para TRUE_FALSE. */
    private Integer selectedIndex;

    /** Índices de opciones elegidas. Para MULTI_SELECT. */
    private List<Integer> selectedIndexes;
}
