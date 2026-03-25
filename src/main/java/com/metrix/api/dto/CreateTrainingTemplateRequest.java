package com.metrix.api.dto;

import com.metrix.api.model.TrainingLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateTrainingTemplateRequest {

    @NotBlank
    private String title;

    private String description;

    private String category;

    @NotNull
    private TrainingLevel level;

    @Min(1) @Max(40)
    private int durationHours;

    @DecimalMin("0") @DecimalMax("10")
    private double minPassGrade;

    @Valid
    @Size(min = 1, message = "La plantilla debe tener al menos un material.")
    private List<TemplateMaterialRequest> materials = new ArrayList<>();

    @Size(max = 10)
    private List<String> tags = new ArrayList<>();
}
