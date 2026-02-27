package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Registro de una transición de estado en el ciclo de vida de una tarea.
 * <p>
 * Embebido en {@link Task#transitions} para habilitar el KPI #9
 * (Velocidad de Corrección): mide el tiempo entre FAILED y la siguiente
 * COMPLETED tras un ciclo de re-trabajo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusTransition {

    /** Estado anterior de la tarea. */
    private TaskStatus fromStatus;

    /** Nuevo estado al que transitó la tarea. */
    private TaskStatus toStatus;

    /** Momento exacto de la transición (UTC). */
    private Instant changedAt;

    /** ID del usuario que realizó el cambio de estado. */
    private String changedBy;
}
