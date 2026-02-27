package com.metrix.api.dto;

import com.metrix.api.model.TrainingStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO para actualizar el progreso de una capacitación — Sprint 10. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTrainingProgressRequest {

    @NotNull
    private TrainingStatus newStatus;

    /** Porcentaje de avance 0–100 (opcional; actualiza avance parcial en EN_CURSO). */
    @Min(0) @Max(100)
    private Integer percentage;

    /** Calificación 0.0–10.0. Obligatoria al marcar COMPLETADA. */
    @DecimalMin("0") @DecimalMax("10")
    private Double grade;

    /** Motivo de no completar. Obligatorio al marcar NO_COMPLETADA. */
    private String comments;
}
