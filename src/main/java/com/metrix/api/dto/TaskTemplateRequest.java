package com.metrix.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Payload para crear o actualizar una plantilla de tarea. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTemplateRequest {

    @NotBlank(message = "El título es obligatorio")
    @Size(min = 4, max = 120, message = "El título debe tener entre 4 y 120 caracteres")
    private String title;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(min = 10, message = "La descripción debe tener al menos 10 caracteres")
    private String description;

    @NotBlank(message = "La categoría es obligatoria")
    private String category;

    @Valid
    @Builder.Default
    private List<TaskTemplateStepDto> steps = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<TaskTemplateMediaDto> media = new ArrayList<>();
}
