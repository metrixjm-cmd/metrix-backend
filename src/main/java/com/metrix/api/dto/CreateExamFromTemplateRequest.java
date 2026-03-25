package com.metrix.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request mínimo para crear un Exam desde una ExamTemplate.
 * La metadata (preguntas, passingScore, timeLimit) se hereda de la plantilla.
 */
@Data
public class CreateExamFromTemplateRequest {

    @NotBlank
    private String storeId;

    /** Override opcional de passingScore. 0 = usar el de la plantilla. */
    private int passingScoreOverride = 0;

    /** Override opcional de timeLimitMinutes. 0 = usar el de la plantilla. */
    private int timeLimitOverride = 0;
}
