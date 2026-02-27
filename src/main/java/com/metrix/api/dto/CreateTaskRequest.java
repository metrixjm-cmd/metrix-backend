package com.metrix.api.dto;

import com.metrix.api.model.TaskCategory;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO de entrada para la creación de tareas (solo ADMIN / GERENTE).
 * <p>
 * Corresponde a los bloques {@code task_definition} + {@code assignment}
 * del modelo unificado METRIX_DEFINICION §4.
 * <p>
 * El campo {@code position} se omite intencionalmente: el servicio lo
 * resuelve automáticamente desde el perfil del usuario asignado para
 * garantizar consistencia con datos históricos (Obj. #11).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {

    // ── task_definition ─────────────────────────────────────────────────

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 200, message = "El título no debe exceder 200 caracteres")
    private String title;

    @NotBlank(message = "La descripción es obligatoria")
    private String description;

    @NotNull(message = "La categoría es obligatoria")
    private TaskCategory category;

    /** {@code false} por defecto. Marcar {@code true} para tareas estratégicas (KPI #8). */
    private boolean critical;

    // ── assignment ──────────────────────────────────────────────────────

    @NotBlank(message = "Debe asignar la tarea a un usuario")
    private String assignedUserId;

    @NotBlank(message = "La tienda es obligatoria")
    private String storeId;

    @NotBlank(message = "El turno es obligatorio")
    private String shift;

    @NotNull(message = "La fecha límite es obligatoria")
    @Future(message = "La fecha límite debe ser una fecha futura")
    private Instant dueAt;
}
