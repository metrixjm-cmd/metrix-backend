package com.metrix.api.dto;

import com.metrix.api.model.TrainingLevel;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** DTO para editar metadata de una capacitacion. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTrainingRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private TrainingLevel level;

    @NotBlank
    private String storeId;

    @NotBlank
    private String shift;

    @NotNull
    @Future
    private Instant dueAt;
}

