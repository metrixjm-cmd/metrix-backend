package com.metrix.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.metrix.api.model.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO de respuesta para tareas.
 * <p>
 * Aplana el documento Task + sub-documentos Execution/Evidence en un objeto
 * plano para que Angular los consuma directamente sin navegación anidada.
 * Omite campos internos de MongoDB ({@code activo}, índices, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private String id;

    // task_definition
    private String title;
    private String description;
    private String category;
    @JsonProperty("isCritical")
    private boolean critical;

    // assignment
    @JsonProperty("assignedToId")
    private String assignedUserId;
    @JsonProperty("assignedToName")
    private String assignedUserName;
    private String position;
    private String storeId;
    private String shift;
    private Instant dueAt;

    // execution (aplanado desde sub-documento)
    private TaskStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private Boolean onTime;
    private List<String> evidenceImages;
    private List<String> evidenceVideos;

    // audit
    private int reworkCount;
    private Double qualityRating;
    private String comments;

    // processes
    @Builder.Default
    private List<ProcessStepResponse> processes = new ArrayList<>();

    // recurrence
    @JsonProperty("isRecurring")
    private boolean recurring;
    @Builder.Default
    private List<String> recurrenceDays = new ArrayList<>();
    private String recurrenceStartTime;
    private String recurrenceEndTime;

    // meta
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
