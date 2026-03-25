package com.metrix.api.dto;

import com.metrix.api.model.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la actualización de estatus de una tarea.
 * <p>
 * Transiciones válidas (validadas en {@code TaskServiceImpl}):
 * <pre>
 *   PENDING     → IN_PROGRESS
 *   IN_PROGRESS → COMPLETED  (solo si now ≤ dueAt)
 *   IN_PROGRESS → FAILED     (requiere comments con la causa)
 * </pre>
 * Los campos {@code comments} y {@code qualityRating} son opcionales en la
 * request, pero {@code TaskServiceImpl} impone {@code comments} como obligatorio
 * al marcar FAILED para forzar trazabilidad operativa (Obj. #20).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest {

    @NotNull(message = "El nuevo estatus es obligatorio")
    private TaskStatus newStatus;

    /**
     * Causa del fallo (obligatorio en FAILED) o nota del evaluador.
     * Validación de presencia al marcar FAILED se realiza en el servicio,
     * no aquí, para poder devolver un mensaje de negocio más descriptivo.
     */
    private String comments;

}
