package com.metrix.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SubmitExamRequest {

    /**
     * Una respuesta por pregunta (mismo orden que exam.questions).
     * Usar selectedIndex para MULTIPLE_CHOICE/TRUE_FALSE,
     * selectedIndexes para MULTI_SELECT, textAnswer para OPEN_TEXT.
     */
    @NotNull
    private List<ExamAnswer> answers;

    /** Tiempo empleado en segundos. Opcional. */
    private Integer timeTakenSeconds;
}
