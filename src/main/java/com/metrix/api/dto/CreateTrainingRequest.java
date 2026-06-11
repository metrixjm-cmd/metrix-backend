package com.metrix.api.dto;

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

    @NotBlank
    private String assignedUserId;

    @NotBlank
    private String storeId;

    @NotBlank
    private String shift;

    /** Fecha de inicio (capacitación multi-día). Opcional — si null se usa dueAt como día único. */
    private Instant startDate;

    @NotNull @FutureOrPresent
    private Instant dueAt;

    private String assignmentGroupId;

    private String examId;

    // ── Opcionales: reutilización de contenido ────────────────────────────

    /** ID de la plantilla de la que viene (opcional). */
    private String templateId;

    /** IDs de materiales del banco a incluir (opcional). */
    private List<String> materialIds = new ArrayList<>();

    private String category;

    private List<String> tags = new ArrayList<>();
}
