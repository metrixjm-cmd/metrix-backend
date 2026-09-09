package com.metrix.api.dto.productos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdjustTrialRequest {

    /** Días a sumar (positivo) o restar (negativo) del fin de prueba. */
    @NotNull(message = "Indica cuántos días sumar o restar")
    @Min(value = -365, message = "No se pueden restar más de 365 días")
    @Max(value = 365, message = "No se pueden sumar más de 365 días")
    private Integer deltaDays;
}
