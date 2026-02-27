package com.metrix.api.dto;

import com.metrix.api.model.TrainingLevel;
import com.metrix.api.model.TrainingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
    private String position;
    private String storeId;
    private String shift;
    private Instant dueAt;

    // Progreso (aplanado de TrainingProgress)
    private TrainingStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Boolean onTime;
    private int percentage;
    private Double grade;
    private Boolean passed;
    private String comments;

    // Auditoría
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
