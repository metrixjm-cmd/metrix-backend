package com.metrix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Paso de proceso dentro de una plantilla de tarea. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTemplateStepDto {

    @NotBlank(message = "El título del paso no puede estar vacío")
    @Size(max = 200, message = "Máximo 200 caracteres")
    private String title;

    @Size(max = 1000, message = "Máximo 1000 caracteres")
    private String description;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private int order;
}
