package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Respuesta de una plantilla de tarea para el frontend. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTemplateResponse {

    private String id;
    private String title;
    private String description;

    /** Valor de categoría del catálogo CATEGORIA (igual que en Task.category). */
    private String category;

    /** Pasos del checklist predefinido. */
    @Builder.Default
    private List<TaskTemplateStepDto> steps = new ArrayList<>();

    @Builder.Default
    private List<TaskTemplateMediaDto> media = new ArrayList<>();

    /** Veces que esta plantilla se usó para crear una tarea. */
    private int timesUsed;

    private String creatorName;
    private Instant createdAt;
    private Instant updatedAt;
}
