package com.metrix.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QualityRatingRequest {

    @NotNull(message = "El rating es obligatorio")
    @DecimalMin(value = "1.0", message = "El rating mínimo es 1.0")
    @DecimalMax(value = "5.0", message = "El rating máximo es 5.0")
    private Double rating;

    private String comments;
}
