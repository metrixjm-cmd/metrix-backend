package com.metrix.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

/**
 * Request para crear una capacitación a partir de una plantilla existente.
 * La metadata (título, nivel, duración, materiales) se hereda de la plantilla.
 * Solo se requiere la asignación específica.
 */
@Data
public class CreateFromTemplateRequest {

    @NotBlank
    private String assignedUserId;

    @NotBlank
    private String storeId;

    @NotBlank
    private String shift;

    @NotNull @Future
    private Instant dueAt;
}
