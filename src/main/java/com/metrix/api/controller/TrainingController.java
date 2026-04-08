package com.metrix.api.controller;

import com.metrix.api.dto.CreateTrainingRequest;
import com.metrix.api.dto.CreateFromTemplateRequest;
import com.metrix.api.dto.TrainingResponse;
import com.metrix.api.dto.UpdateTrainingProgressRequest;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.service.TrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import java.util.List;

/**
 * Endpoints del módulo de Capacitación — Sprint 10.
 *
 * GET    /api/v1/trainings/my              → mis capacitaciones       (any auth)
 * GET    /api/v1/trainings/store/{storeId} → por sucursal             (ADMIN, GERENTE)
 * GET    /api/v1/trainings/{id}            → detalle                  (any auth)
 * POST   /api/v1/trainings                 → crear                    (ADMIN, GERENTE)
 * PATCH  /api/v1/trainings/{id}/progress   → actualizar progreso      (any auth)
 * DELETE /api/v1/trainings/{id}            → soft-delete              (ADMIN)
 */
@RestController
@RequestMapping("/api/v1/trainings")
@RequiredArgsConstructor
@Tag(name = "Capacitación", description = "Módulo de capacitación (Sprint 10)")
public class TrainingController {

    private final TrainingService trainingService;
    private final UserRepository  userRepository;

    // ── Mis capacitaciones ───────────────────────────────────────────────

    @Operation(summary = "Mis capacitaciones",
               description = "Devuelve las capacitaciones asignadas al usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de capacitaciones obtenida exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping("/my")
    public ResponseEntity<List<TrainingResponse>> getMyTrainings(Authentication auth) {
        String userId = resolveUserId(auth.getName());
        return ResponseEntity.ok(trainingService.getMyTrainings(userId));
    }

    // ── Vista global ADMIN (todas las sucursales) ────────────────────────

    @Operation(summary = "Todas las capacitaciones (ADMIN)",
               description = "Devuelve todas las capacitaciones activas del sistema. Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista completa de capacitaciones obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TrainingResponse>> getAll() {
        return ResponseEntity.ok(trainingService.getAll());
    }

    // ── Por sucursal (gerencial) ─────────────────────────────────────────

    @Operation(summary = "Capacitaciones por sucursal",
               description = "Devuelve las capacitaciones de una sucursal específica. ADMIN o GERENTE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de capacitaciones de la sucursal obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    @GetMapping("/store/{storeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<TrainingResponse>> getByStore(@Parameter(description = "ID de la sucursal") @PathVariable String storeId) {
        return ResponseEntity.ok(trainingService.getByStore(storeId));
    }

    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<TrainingResponse>> getByAssignmentGroupId(@PathVariable String groupId) {
        return ResponseEntity.ok(trainingService.getByAssignmentGroupId(groupId));
    }

    // ── Detalle ──────────────────────────────────────────────────────────

    @Operation(summary = "Detalle de capacitación",
               description = "Devuelve la información completa de una capacitación por su ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de capacitación obtenido exitosamente"),
            @ApiResponse(responseCode = "404", description = "Capacitación no encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TrainingResponse> getById(@Parameter(description = "ID de la capacitación") @PathVariable String id) {
        return ResponseEntity.ok(trainingService.getById(id));
    }

    // ── Crear ────────────────────────────────────────────────────────────

    @Operation(summary = "Crear capacitación",
               description = "Programa una nueva capacitación para una sucursal. ADMIN o GERENTE.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Capacitación creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<TrainingResponse> create(
            @Valid @RequestBody CreateTrainingRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(trainingService.create(request, auth.getName()));
    }

    // ── Actualizar Progreso ──────────────────────────────────────────────

    @Operation(summary = "Actualizar progreso",
               description = "Actualiza el estado de progreso de una capacitación (completar, no completar, calificar).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progreso actualizado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Capacitación no encontrada"),
            @ApiResponse(responseCode = "400", description = "Transición de estado inválida")
    })
    @PatchMapping("/{id}/progress")
    public ResponseEntity<TrainingResponse> updateProgress(
            @Parameter(description = "ID de la capacitación") @PathVariable String id,
            @Valid @RequestBody UpdateTrainingProgressRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                trainingService.updateProgress(id, request, auth.getName()));
    }

    // ── Endpoints paginados ───────────────────────────────────────────────

    @Operation(summary = "Mis capacitaciones (paginado)")
    @GetMapping("/my/paged")
    public ResponseEntity<Page<TrainingResponse>> getMyTrainingsPaged(
            Authentication auth,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size) {
        String userId = resolveUserId(auth.getName());
        return ResponseEntity.ok(trainingService.getMyTrainingsPaged(userId, page, size));
    }

    @Operation(summary = "Capacitaciones por sucursal (paginado)")
    @GetMapping("/store/{storeId}/paged")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<Page<TrainingResponse>> getByStorePaged(
            @PathVariable String storeId,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size) {
        return ResponseEntity.ok(trainingService.getByStorePaged(storeId, page, size));
    }

    @Operation(summary = "Todas las capacitaciones (paginado, solo ADMIN)")
    @GetMapping("/paged")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<TrainingResponse>> getAllPaged(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size) {
        return ResponseEntity.ok(trainingService.getAllPaged(page, size));
    }

    // ── Crear desde plantilla ─────────────────────────────────────────────

    @Operation(summary = "Crear capacitación desde plantilla",
               description = "Hereda metadata y materiales de la plantilla. Solo se requiere asignación (usuario, sucursal, turno, fecha). ADMIN o GERENTE.")
    @PostMapping("/from-template/{templateId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<TrainingResponse> createFromTemplate(
            @PathVariable String templateId,
            @Valid @RequestBody CreateFromTemplateRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(trainingService.createFromTemplate(
                        templateId,
                        request.getAssignedUserId(),
                        request.getStoreId(),
                        request.getShift(),
                        request.getDueAt(),
                        request.getAssignmentGroupId(),
                        auth.getName()));
    }

    // ── Marcar material como visto ────────────────────────────────────────

    @Operation(summary = "Marcar material como visto",
               description = "El ejecutador asignado marca un material de la capacitación como visto.")
    @PatchMapping("/{id}/materials/{materialId}/view")
    public ResponseEntity<TrainingResponse> markMaterialViewed(
            @PathVariable String id,
            @PathVariable String materialId,
            Authentication auth) {
        return ResponseEntity.ok(
                trainingService.markMaterialViewed(id, materialId, auth.getName()));
    }

    // ── Soft-delete ──────────────────────────────────────────────────────

    @Operation(summary = "Eliminar capacitación",
               description = "Desactiva una capacitación sin eliminarla de la base de datos (soft-delete). Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Capacitación eliminada exitosamente"),
            @ApiResponse(responseCode = "404", description = "Capacitación no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@Parameter(description = "ID de la capacitación") @PathVariable String id) {
        trainingService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // ── Helper: resuelve MongoDB _id desde numeroUsuario ─────────────────

    private String resolveUserId(String numeroUsuario) {
        return userRepository.findByNumeroUsuario(numeroUsuario)
                .map(u -> u.getId())
                .orElse(numeroUsuario);
    }
}
