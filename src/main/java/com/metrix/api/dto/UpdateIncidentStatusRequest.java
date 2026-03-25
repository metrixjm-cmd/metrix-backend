package com.metrix.api.dto;

import com.metrix.api.model.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateIncidentStatusRequest {

    @NotNull
    private IncidentStatus newStatus;

    /** Obligatorio cuando newStatus == CERRADA. */
    private String resolutionNotes;

    /** Nombre de quien realizó el cierre. Si se provee, sobreescribe el del usuario autenticado. */
    private String closedByName;

    /** Notas opcionales de la transición para el historial. */
    private String notes;
}
