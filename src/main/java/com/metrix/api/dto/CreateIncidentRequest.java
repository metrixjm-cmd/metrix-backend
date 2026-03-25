package com.metrix.api.dto;

import com.metrix.api.model.ImplicadoEntry;
import com.metrix.api.model.IncidentCategory;
import com.metrix.api.model.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateIncidentRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private IncidentCategory category;

    @NotNull
    private IncidentSeverity severity;

    /** ID de tarea relacionada. Opcional. */
    private String taskId;

    @NotBlank
    private String storeId;

    @NotBlank
    private String shift;

    /** Personas involucradas en el evento (con tipo y responsabilidad). Opcional. */
    private List<ImplicadoEntry> implicados;

    /** Nombre o identificador del responsable de seguimiento. Opcional. */
    private String followUpResponsible;
}
