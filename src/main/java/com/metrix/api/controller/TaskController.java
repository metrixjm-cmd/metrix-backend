package com.metrix.api.controller;

import com.metrix.api.dto.CreateTaskRequest;
import com.metrix.api.dto.TaskResponse;
import com.metrix.api.dto.UpdateStatusRequest;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    @GetMapping("/my")
    public ResponseEntity<List<TaskResponse>> getMyTasks(
            @RequestParam(required = false) String shift,
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
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<TaskResponse>> getTasksByUser(
            @PathVariable String userId,
            @RequestParam(required = false) String shift) {

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
    @GetMapping("/store/{storeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<TaskResponse>> getTasksByStore(
            @PathVariable String storeId,
            @RequestParam(required = false) String shift) {

        List<TaskResponse> tasks = (shift != null && !shift.isBlank())
                ? taskService.getTasksByStoreAndShift(storeId, shift)
                : taskService.getTasksByStore(storeId);

        return ResponseEntity.ok(tasks);
    }

    /**
     * GET /api/v1/tasks/{taskId}
     * <p>
     * Detalle completo de una tarea: definición, asignación, estado de ejecución y auditoría.
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getById(@PathVariable String taskId) {
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
    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskResponse> updateStatus(
            @PathVariable String taskId,
            @Valid @RequestBody UpdateStatusRequest request,
            Authentication auth) {

        TaskResponse response = taskService.updateStatus(taskId, request, auth.getName());
        return ResponseEntity.ok(response);
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
