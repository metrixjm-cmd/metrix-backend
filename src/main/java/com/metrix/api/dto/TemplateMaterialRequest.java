package com.metrix.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Material dentro de una plantilla — usado en creación y actualización. */
@Data
public class TemplateMaterialRequest {

    @NotBlank
    private String materialId;

    @Min(1)
    private int order;

    private boolean required = true;

    private String notes;
}
