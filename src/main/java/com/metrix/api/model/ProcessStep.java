package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Paso de proceso embebido en una Task.
 * Cada paso tiene tags que determinan qué perfiles lo ven como checklist.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessStep {

    @Field("step_id")
    private String stepId;

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Builder.Default
    @Field("tags")
    private List<String> tags = new ArrayList<>();

    /** true cuando el ejecutor marca este paso como completado. */
    @Builder.Default
    @Field("completed")
    private boolean completed = false;

    /** Notas opcionales del ejecutor al completar el paso. */
    @Field("notes")
    private String notes;

    @Field("order")
    private int order;

    /** Timestamp de cuando se completó este paso. */
    @Field("completed_at")
    private Instant completedAt;
}
