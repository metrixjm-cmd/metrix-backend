package com.metrix.api.controller;

import com.metrix.api.dto.EvidenceUploadResponse;
import com.metrix.api.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * Controller REST para upload de evidencias multimedia de ejecución (Sprint 5).
 * <p>
 * Base path: {@code /api/v1/tasks/{taskId}/evidence}
 * <p>
 * Restricciones de acceso:
 * <ul>
 *   <li>Solo {@code EJECUTADOR} puede subir evidencias.</li>
 *   <li>El EJECUTADOR debe ser el colaborador asignado a la tarea.</li>
 *   <li>La tarea debe estar en estado {@code IN_PROGRESS}.</li>
 * </ul>
 * <p>
 * Tipos permitidos:
 * <ul>
 *   <li>Imágenes: JPEG, PNG, WebP — máximo 10 MB.</li>
 *   <li>Videos: MP4, QuickTime, WebM — máximo 50 MB.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/evidence")
@RequiredArgsConstructor
@Tag(name = "Evidencias", description = "Upload de evidencias multimedia (Sprint 5)")
public class EvidenceController {

    private final TaskService taskService;

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // 10 MB
    private static final long MAX_VIDEO_BYTES = 50L * 1024 * 1024;   // 50 MB

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private static final Set<String> ALLOWED_VIDEO_TYPES =
            Set.of("video/mp4", "video/quicktime", "video/webm");

    /**
     * POST /api/v1/tasks/{taskId}/evidence
     * <p>
     * Sube una imagen o video como evidencia de ejecución de una tarea.
     *
     * @param taskId   MongoDB _id de la tarea en ejecución
     * @param file     archivo multimedia (multipart)
     * @param type     tipo de evidencia: IMAGE | VIDEO
     * @param auth     contexto de autenticación JWT del EJECUTADOR
     * @return {@link EvidenceUploadResponse} con la URL persistida en GCS
     */
    @Operation(summary = "Subir evidencia de ejecución (imagen o video)", description = "Sube una imagen o video como evidencia de ejecución de una tarea. Solo EJECUTADOR asignado a la tarea, que debe estar en estado IN_PROGRESS.")
    @ApiResponse(responseCode = "201", description = "Evidencia subida exitosamente a GCS")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EJECUTADOR')")
    public ResponseEntity<EvidenceUploadResponse> upload(
            @PathVariable String taskId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            Authentication auth) throws IOException {

        String mediaType = type.trim().toUpperCase();
        validateMediaType(mediaType);
        validateMimeType(mediaType, file.getContentType());
        validateFileSize(mediaType, file.getSize());

        String extension = resolveExtension(file.getOriginalFilename(), mediaType);
        byte[] bytes     = file.getBytes();

        EvidenceUploadResponse response = taskService.addEvidence(
                taskId, mediaType, bytes, file.getContentType(), extension, auth.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Validaciones ─────────────────────────────────────────────────────

    private void validateMediaType(String mediaType) {
        if (!mediaType.equals("IMAGE") && !mediaType.equals("VIDEO")) {
            throw new IllegalArgumentException(
                    "El parámetro 'type' debe ser IMAGE o VIDEO. Recibido: " + mediaType);
        }
    }

    private void validateMimeType(String mediaType, String contentType) {
        if (contentType == null) {
            throw new IllegalArgumentException("No se pudo determinar el tipo de archivo.");
        }
        boolean valid = mediaType.equals("IMAGE")
                ? ALLOWED_IMAGE_TYPES.contains(contentType)
                : ALLOWED_VIDEO_TYPES.contains(contentType);

        if (!valid) {
            String allowed = mediaType.equals("IMAGE")
                    ? "image/jpeg, image/png, image/webp"
                    : "video/mp4, video/quicktime, video/webm";
            throw new IllegalArgumentException(
                    "Tipo MIME no permitido: " + contentType + ". Permitidos: " + allowed);
        }
    }

    private void validateFileSize(String mediaType, long size) {
        long maxBytes = mediaType.equals("IMAGE") ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
        if (size > maxBytes) {
            throw new IllegalArgumentException(String.format(
                    "El archivo excede el límite de %dMB para %s. Tamaño recibido: %.2f MB.",
                    maxBytes / (1024 * 1024), mediaType, size / 1_048_576.0));
        }
    }

    private String resolveExtension(String originalName, String mediaType) {
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        }
        return mediaType.equals("IMAGE") ? "jpg" : "mp4";
    }
}
