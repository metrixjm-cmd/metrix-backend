package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del endpoint POST /api/v1/tasks/{taskId}/evidence.
 * Confirma la URL persistida en GCS y el tipo de evidencia subida.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceUploadResponse {

    /** MongoDB _id de la tarea a la que pertenece esta evidencia. */
    private String taskId;

    /** Tipo de archivo subido: IMAGE | VIDEO */
    private String type;

    /** URL pública del objeto en Google Cloud Storage. */
    private String url;
}
