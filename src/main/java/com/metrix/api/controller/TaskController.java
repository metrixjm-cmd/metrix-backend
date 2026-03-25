package com.metrix.api.controller;

import com.metrix.api.dto.CreateTaskRequest;
import com.metrix.api.dto.QualityRatingRequest;
import com.metrix.api.dto.TaskResponse;
import com.metrix.api.dto.UpdateStatusRequest;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST del Motor de Tareas METRIX (Sprint 2).
 * <p>
 * Base path: {@code /api/v1/tasks}
 * <p>
 * Matriz de acceso por endpoint:
 * <pre>
 *   POST   /              → ADMIN, GERENTE (crear tarea)
 *   GET    /my            → Cualquier autenticado (propio portfolio, filtro ?shift opcional)
 *   GET    /user/{id}     → ADMIN, GERENTE (portfolio de cualquier colaborador)
 *   GET    /store/{id}    → ADMIN, GERENTE (todas las tareas de una sucursal)
 *   GET    /{id}          → Cualquier autenticado (detalle de tarea)
 *   PATCH  /{id}/status   → Cualquier autenticado (actualizar estado con validación en service)
 * </pre>
 * El controller es un adaptador delgado (Clean Architecture): valida DTOs y delega
 * al {@link TaskService}. Nunca contiene lógica de negocio.
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tareas", description = "Motor de tareas METRIX: creación, consulta, cambio de estado y calificación de calidad.")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService    taskService;
    private final UserRepository userRepository;

    // ── Crear Tarea ──────────────────────────────────────────────────────

    /**
     * POST /api/v1/tasks
     * <p>
     * Crea y asigna una tarea a un colaborador.
     * El {@code createdBy} se resuelve automáticamente desde el JWT del creador.
     */
    @Operation(summary = "Crear tarea", description = "Crea y asigna una tarea a un colaborador. El campo createdBy se resuelve automáticamente desde el JWT.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tarea creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de la tarea inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos — requiere rol ADMIN o GERENTE")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            Authentication auth) {

        String creatorId = resolveUserId(auth.getName());
        TaskResponse response = taskService.createTask(request, creatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Consultas ────────────────────────────────────────────────────────

    /**
     * GET /api/v1/tasks/my?shift=MATUTINO
     * <p>
     * Devuelve las tareas del usuario autenticado.
     * El parámetro {@code shift} es opcional: si se omite, devuelve todos los turnos.
     * Caso de uso principal del EJECUTADOR (Obj. #1, #7).
     */
    @Operation(summary = "Mis tareas", description = "Devuelve las tareas asignadas al usuario autenticado, con filtro de turno opcional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de tareas del usuario")
    })
    @GetMapping("/my")
    public ResponseEntity<List<TaskResponse>> getMyTasks(
            @Parameter(description = "Filtro de turno (MATUTINO, VESPERTINO, NOCTURNO). Opcional.") @RequestParam(required = false) String shift,
            Authentication auth) {

        String userId = resolveUserId(auth.getName());
        List<TaskResponse> tasks = (shift != null && !shift.isBlank())
                ? taskService.getTasksByUserAndShift(userId, shift)
                : taskService.getTasksByUser(userId);

        return ResponseEntity.ok(tasks);
    }

    /**
     * GET /api/v1/tasks/user/{userId}?shift=VESPERTINO
     * <p>
     * Vista gerencial: portfolio de cualquier colaborador con filtro de turno opcional.
     */
    @Operation(summary = "Tareas por usuario", description = "Vista gerencial: devuelve el portfolio de tareas de cualquier colaborador, con filtro de turno opcional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de tareas del colaborador"),
            @ApiResponse(responseCode = "403", description = "Sin permisos — requiere rol ADMIN o GERENTE")
    })
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<TaskResponse>> getTasksByUser(
            @Parameter(description = "ID del usuario (MongoDB _id)") @PathVariable String userId,
            @Parameter(description = "Filtro de turno opcional") @RequestParam(required = false) String shift) {

        List<TaskResponse> tasks = (shift != null && !shift.isBlank())
                ? taskService.getTasksByUserAndShift(userId, shift)
                : taskService.getTasksByUser(userId);

        return ResponseEntity.ok(tasks);
    }

    /**
     * GET /api/v1/tasks/store/{storeId}?shift=NOCTURNO
     * <p>
     * Vista gerencial: todas las tareas activas de una sucursal con filtro de turno opcional.
     * Soporta Multi-Administración (Obj. #6): un ADMIN puede ver cualquier sucursal.
     */
    @Operation(summary = "Tareas por sucursal", description = "Vista gerencial: todas las tareas activas de una sucursal con filtro de turno y paginación opcional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista paginada de tareas de la sucursal"),
            @ApiResponse(responseCode = "403", description = "Sin permisos — requiere rol ADMIN o GERENTE")
    })
    @GetMapping("/store/{storeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<?> getTasksByStore(
            @Parameter(description = "ID de la sucursal") @PathVariable String storeId,
            @Parameter(description = "Filtro de turno opcional") @RequestParam(required = false) String shift,
            @Parameter(description = "Numero de pagina (base 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamano de pagina (maximo 500)") @RequestParam(defaultValue = "500") int size) {

        int safeSize = Math.min(size, 500);

        // If shift filter is specified, use non-paginated (shift lists are small)
        if (shift != null && !shift.isBlank()) {
            return ResponseEntity.ok(taskService.getTasksByStoreAndShift(storeId, shift));
        }

        // DB-level pagination for unfiltered store queries
        Page<TaskResponse> result = taskService.getTasksByStore(storeId,
                PageRequest.of(page, safeSize, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/tasks/{taskId}
     * <p>
     * Detalle completo de una tarea: definición, asignación, estado de ejecución y auditoría.
     */
    @Operation(summary = "Detalle de tarea", description = "Devuelve el detalle completo de una tarea: definición, asignación, estado de ejecución y auditoría.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de la tarea"),
            @ApiResponse(responseCode = "404", description = "Tarea no encontrada")
    })
    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getById(@Parameter(description = "ID de la tarea") @PathVariable String taskId) {
        return ResponseEntity.ok(taskService.getById(taskId));
    }

    // ── Actualizar Estatus ───────────────────────────────────────────────

    /**
     * PATCH /api/v1/tasks/{taskId}/status
     * <p>
     * Actualiza el estado de una tarea con timestamps automáticos.
     * El {@link com.metrix.api.service.TaskServiceImpl} aplica las reglas de negocio:
     * validación de transición, cálculo de onTime y obligatoriedad de comentarios en FAILED.
     * <p>
     * Ejemplo de body para iniciar ejecución:
     * <pre>{ "newStatus": "IN_PROGRESS" }</pre>
     * Ejemplo para completar:
     * <pre>{ "newStatus": "COMPLETED", "qualityRating": 4.5 }</pre>
     * Ejemplo para reportar fallo (comments obligatorio):
     * <pre>{ "newStatus": "FAILED", "comments": "Equipo de limpieza no disponible." }</pre>
     */
    @Operation(summary = "Actualizar estado", description = "Actualiza el estado de una tarea con timestamps automáticos. Valida transiciones permitidas y obligatoriedad de comentarios en FAILED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Transición de estado inválida o datos faltantes"),
            @ApiResponse(responseCode = "422", description = "Regla de negocio violada (ej. comentarios obligatorios para FAILED)")
    })
    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskResponse> updateStatus(
            @Parameter(description = "ID de la tarea") @PathVariable String taskId,
            @Valid @RequestBody UpdateStatusRequest request,
            Authentication auth) {

        TaskResponse response = taskService.updateStatus(taskId, request, auth.getName());
        return ResponseEntity.ok(response);
    }

    // ── Calificación de Calidad (Sprint 18) ──────────────────────────────

    /**
     * PATCH /api/v1/tasks/{taskId}/quality
     * <p>
     * Permite a GERENTE/ADMIN calificar la calidad de una tarea COMPLETADA.
     * Alimenta el Pilar Calidad del IGEO analítico.
     * <p>
     * Ejemplo de body: {@code { "rating": 4.5, "comments": "Excelente ejecución." }}
     */
    @Operation(summary = "Calificar calidad", description = "Permite a GERENTE/ADMIN calificar la calidad de una tarea COMPLETADA. Alimenta el Pilar Calidad del IGEO analítico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Calificación registrada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de calificación inválidos o tarea no está en estado COMPLETED"),
            @ApiResponse(responseCode = "403", description = "Sin permisos — requiere rol ADMIN o GERENTE")
    })
    @PatchMapping("/{taskId}/quality")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<TaskResponse> rateQuality(
            @Parameter(description = "ID de la tarea") @PathVariable String taskId,
            @Valid @RequestBody QualityRatingRequest request,
            Authentication auth) {

        return ResponseEntity.ok(taskService.rateQuality(taskId, request, auth.getName()));
    }

    // ── Checklist: Actualizar paso de proceso ───────────────────────────

    @PatchMapping("/{taskId}/process/{stepId}")
    public ResponseEntity<TaskResponse> updateProcessStep(
            @PathVariable String taskId,
            @PathVariable String stepId,
            @RequestBody com.metrix.api.dto.UpdateProcessStepRequest request) {

        return ResponseEntity.ok(taskService.updateProcessStep(
                taskId, stepId, request.isCompleted(), request.getNotes()));
    }

    // ── Admin: Editar paso de proceso ───────────────────────────────────

    @PutMapping("/{taskId}/process/{stepId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TaskResponse> editProcessStep(
            @PathVariable String taskId,
            @PathVariable String stepId,
            @RequestBody com.metrix.api.dto.ProcessStepRequest request) {
        return ResponseEntity.ok(taskService.editProcessStep(
                taskId, stepId, request.getTitle(), request.getDescription()));
    }

    // ── Admin: Eliminar paso de proceso ─────────────────────────────────

    @DeleteMapping("/{taskId}/process/{stepId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TaskResponse> deleteProcessStep(
            @PathVariable String taskId,
            @PathVariable String stepId) {
        return ResponseEntity.ok(taskService.deleteProcessStep(taskId, stepId));
    }

    // ── Admin: Soft-delete tarea ─────────────────────────────────────────

    @PatchMapping("/{taskId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateTask(@PathVariable String taskId) {
        taskService.deactivateTask(taskId);
        return ResponseEntity.noContent().build();
    }

    // ── Admin: Todas las tareas del sistema ─────────────────────────────

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size) {

        int safeSize = Math.min(size, 500);
        if (page == 0 && safeSize >= 500) {
            // Backward compat: return full list when no pagination requested
            return ResponseEntity.ok(taskService.getAllTasks());
        }
        return ResponseEntity.ok(taskService.getAllTasks(
                PageRequest.of(page, safeSize, Sort.by("createdAt").descending())));
    }

    // ── Admin: Purge all tasks ─────────────────────────────────────────

    @DeleteMapping("/admin/purge")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> purgeAllTasks() {
        taskService.deleteAll();
        return ResponseEntity.noContent().build();
    }

    // ── Helper ───────────────────────────────────────────────────────────

    /**
     * Traduce el {@code numeroUsuario} (subject del JWT / {@code auth.getName()})
     * al MongoDB {@code _id} del usuario, necesario para las queries del repositorio.
     *
     * @throws ResourceNotFoundException si el usuario no existe en base de datos
     */
    private String resolveUserId(String numeroUsuario) {
        return userRepository.findByNumeroUsuario(numeroUsuario)
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado en base de datos: " + numeroUsuario));
    }
}
