package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Registro inmutable de una transición de estado en el ciclo de vida de una incidencia.
 * Documento embebido en {@link Incident#transitions} — no tiene @Document propio.
 * Sigue el mismo patrón que {@link StatusTransition} (Sprint 8).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentTransition {

    @Field("from_status")
    private IncidentStatus fromStatus;

    @Field("to_status")
    private IncidentStatus toStatus;

    @Field("changed_at")
    private Instant changedAt;

    /** numeroUsuario del actor que realizó la transición. */
    @Field("changed_by")
    private String changedBy;

    /** Comentarios opcionales de la transición. */
    @Field("notes")
    private String notes;
}
