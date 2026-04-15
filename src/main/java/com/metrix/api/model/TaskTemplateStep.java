package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

/**
 * Paso de proceso embebido en una TaskTemplate.
 * Replica la estructura de {@link ProcessStep} pero sin campos de ejecución
 * (completed, notes, completedAt) ya que es una plantilla, no una instancia.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTemplateStep {

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Builder.Default
    @Field("tags")
    private List<String> tags = new ArrayList<>();

    @Field("order")
    private int order;
}
