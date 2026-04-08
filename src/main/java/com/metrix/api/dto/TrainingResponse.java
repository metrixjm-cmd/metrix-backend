package com.metrix.api.dto;

import com.metrix.api.model.TrainingLevel;
import com.metrix.api.model.TrainingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** Respuesta aplanada de Training + TrainingProgress — Sprint 10. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingResponse {

    private String id;

    // Definición
    private String title;
    private String description;
    private TrainingLevel level;
    private int durationHours;
    private double minPassGrade;

    // Asignación
    private String assignedUserId;
    private String assignedUserName;
    private String position;
    private String storeId;
    private String shift;
    private Instant dueAt;
    private String assignmentGroupId;

    // Progreso (aplanado de TrainingProgress)
    private TrainingStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Boolean onTime;
    private int percentage;
    private Double grade;
    private Boolean passed;
    private String comments;

    // Materiales y plantilla
    private String templateId;
    private List<TrainingMaterialRefResponse> materials;
    private String category;
    private List<String> tags;

    // Auditoría
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
