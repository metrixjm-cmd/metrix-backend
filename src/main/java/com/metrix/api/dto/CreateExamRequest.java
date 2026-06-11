package com.metrix.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import com.metrix.api.model.ExamAudience;

import java.util.List;

@Data
public class CreateExamRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    private String description;

    /** ID de capacitación relacionada. Opcional. */
    private String trainingId;

    @NotBlank
    private String storeId;

    @NotNull
    private ExamAudience targetAudience;

    @NotNull
    @Size(min = 5, message = "El examen debe tener al menos 5 preguntas")
    @Valid
    private List<ExamQuestionDto> questions;

    /** Porcentaje mínimo para aprobar (0–100). */
    @Min(0) @Max(100)
    private int passingScore = 70;

    /** Límite de tiempo en minutos. Null = sin límite. */
    @Min(1)
    private Integer timeLimitMinutes;
}
