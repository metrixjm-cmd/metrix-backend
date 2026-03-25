package com.metrix.api.dto;

import com.metrix.api.model.TrainingLevel;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** DTO de creación de una capacitación — Sprint 10. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTrainingRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private TrainingLevel level;

    @Min(1) @Max(40)
    private int durationHours;

    @DecimalMin("0") @DecimalMax("10")
    private double minPassGrade;

    @NotBlank
    private String assignedUserId;

    @NotBlank
    private String storeId;

    @NotBlank
    private String shift;

    @NotNull @Future
    private Instant dueAt;

    // ── Opcionales: reutilización de contenido ────────────────────────────

    /** ID de la plantilla de la que viene (opcional). */
    private String templateId;

    /** IDs de materiales del banco a incluir (opcional). */
    private List<String> materialIds = new ArrayList<>();

    private String category;

    private List<String> tags = new ArrayList<>();
}
