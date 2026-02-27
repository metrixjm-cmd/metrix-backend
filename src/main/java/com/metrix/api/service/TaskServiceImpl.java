package com.metrix.api.service;

import com.metrix.api.dto.CreateTaskRequest;
import com.metrix.api.dto.EvidenceUploadResponse;
import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.dto.TaskResponse;
import com.metrix.api.dto.UpdateStatusRequest;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.*;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Implementación del motor de tareas para METRIX (Sprint 2).
 * <p>
 * Principios SOLID aplicados:
 * <ul>
 *   <li>SRP: Solo gestiona el ciclo de vida de tareas. KPIs van en un servicio separado (Sprint 5).</li>
 *   <li>OCP: Las reglas de transición están en métodos privados cohesivos;
 *       agregar un nuevo estado solo requiere añadir un caso al switch, sin modificar lógica existente.</li>
 *   <li>LSP: Sustituible por cualquier otra implementación de {@link TaskService}.</li>
 *   <li>DIP: Depende de {@link TaskRepository} y {@link UserRepository} (abstracciones),
 *       no de MongoTemplate ni clases concretas de infraestructura.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository    taskRepository;
    private final UserRepository    userRepository;
    private final GcsService        gcsService;
    private final NotificationService notificationService;

    // ── Crear Tarea ──────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     * <p>
     * El {@code position} se resuelve desde el perfil del usuario asignado
     * y se desnormaliza en el documento Task para garantizar la consistencia
     * de reportes históricos aunque el puesto del usuario cambie (Obj. #11).
     */
    @Override
    public TaskResponse createTask(CreateTaskRequest request, String createdBy) {
        User assignedUser = userRepository.findById(request.getAssignedUserId())
                .filter(User::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario asignado no encontrado o inactivo: " + request.getAssignedUserId()));

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .critical(request.isCritical())
                .assignedUserId(request.getAssignedUserId())
                .position(assignedUser.getPuesto())     // Desnormalizado para reportes históricos
                .storeId(request.getStoreId())
                .shift(request.getShift())
                .dueAt(request.getDueAt())
                .execution(Execution.builder()
                        .status(TaskStatus.PENDING)
                        .evidence(new Evidence())
                        .build())
                .createdBy(createdBy)
                .activo(true)
                .build();

        Task saved = taskRepository.save(task);

        // Notifica al EJECUTADOR asignado (Sprint 6)
        notificationService.sendToUser(saved.getAssignedUserId(), NotificationEvent.builder()
                .id(UUID.randomUUID().toString())
                .type("TASK_ASSIGNED")
                .severity("info")
                .title("Nueva tarea asignada")
                .body(saved.getTitle() + " · " + saved.getShift())
                .taskId(saved.getId())
                .storeId(saved.getStoreId())
                .timestamp(Instant.now())
                .build());

        return toResponse(saved);
    }

    // ── Consultas ────────────────────────────────────────────────────────

    @Override
    public List<TaskResponse> getTasksByUser(String assignedUserId) {
        return taskRepository.findByAssignedUserIdAndActivoTrue(assignedUserId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<TaskResponse> getTasksByUserAndShift(String assignedUserId, String shift) {
        return taskRepository.findByAssignedUserIdAndShiftAndActivoTrue(assignedUserId, shift)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<TaskResponse> getTasksByStore(String storeId) {
        return taskRepository.findByStoreIdAndActivoTrue(storeId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<TaskResponse> getTasksByStoreAndShift(String storeId, String shift) {
        return taskRepository.findByStoreIdAndShiftAndActivoTrue(storeId, shift)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public TaskResponse getById(String taskId) {
        return taskRepository.findById(taskId)
                .filter(Task::isActivo)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada: " + taskId));
    }

    // ── Actualizar Estatus ───────────────────────────────────────────────

    /**
     * {@inheritDoc}
     * <p>
     * Flujo del método:
     * <ol>
     *   <li>Carga y valida que la tarea exista y esté activa.</li>
     *   <li>Valida que la transición de estado sea legal.</li>
     *   <li>Delega en el método {@code apply*} correspondiente para
     *       aplicar timestamps y lógica de negocio específica del estado.</li>
     *   <li>Persiste y retorna el documento actualizado.</li>
     * </ol>
     */
    @Override
    public TaskResponse updateStatus(String taskId, UpdateStatusRequest request, String currentUser) {
        Task task = taskRepository.findById(taskId)
                .filter(Task::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada: " + taskId));

        Execution execution = task.getExecution();
        TaskStatus current  = execution.getStatus();
        TaskStatus next     = request.getNewStatus();

        validateTransition(current, next);

        Instant now = Instant.now();

        switch (next) {
            case IN_PROGRESS -> applyInProgress(execution, now);
            case COMPLETED   -> applyCompleted(execution, task.getDueAt(), now);
            case FAILED      -> {
                applyFailed(execution, now, request.getComments());
                task.setReworkCount(task.getReworkCount() + 1);  // KPI #3: Tasa de Re-trabajo
            }
            case PENDING     -> applyReopened(execution);  // FAILED → PENDING (rework, KPI #9)
        }

        // Registrar la transición de estado para historial (KPI #9)
        task.getTransitions().add(StatusTransition.builder()
                .fromStatus(current)
                .toStatus(next)
                .changedAt(now)
                .changedBy(currentUser)
                .build());

        if (request.getComments() != null && !request.getComments().isBlank()) {
            task.setComments(request.getComments());
        }
        if (request.getQualityRating() != null) {
            task.setQualityRating(request.getQualityRating());
        }

        Task saved = taskRepository.save(task);

        // Notificaciones en tiempo real por cambio de estado (Sprint 6)
        emitStatusNotification(saved, next);

        return toResponse(saved);
    }

    // ── Lógica de Transiciones ───────────────────────────────────────────

    /**
     * Valida que la transición solicitada sea legal según el flujo definido.
     * <p>
     * COMPLETED y FAILED son estados terminales: no admiten más transiciones.
     *
     * @throws IllegalStateException si la transición no está permitida
     */
    private void validateTransition(TaskStatus current, TaskStatus next) {
        boolean valid = switch (current) {
            case PENDING     -> next == TaskStatus.IN_PROGRESS;
            case IN_PROGRESS -> next == TaskStatus.COMPLETED || next == TaskStatus.FAILED;
            case FAILED      -> next == TaskStatus.PENDING;   // re-abrir para rework (KPI #9)
            case COMPLETED   -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    String.format(
                            "Transición inválida: %s → %s. " +
                            "Flujo permitido: PENDING→IN_PROGRESS, IN_PROGRESS→COMPLETED|FAILED, FAILED→PENDING.",
                            current, next));
        }
    }

    /**
     * PENDING → IN_PROGRESS
     * Registra el inicio de la ejecución (KPI #4: Tiempo Promedio de Ejecución).
     */
    private void applyInProgress(Execution execution, Instant now) {
        execution.setStatus(TaskStatus.IN_PROGRESS);
        execution.setStartedAt(now);
    }

    /**
     * IN_PROGRESS → COMPLETED
     * <p>
     * Regla de negocio (Obj. #4 + KPI #1):
     * Una tarea solo puede marcarse COMPLETED si {@code now ≤ dueAt}.
     * Si el plazo venció, el sistema rechaza la transición: el operador
     * debe usar FAILED para registrar la ejecución tardía con su causa.
     *
     * @throws IllegalStateException si el plazo ya venció
     */
    private void applyCompleted(Execution execution, Instant dueAt, Instant now) {
        if (now.isAfter(dueAt)) {
            throw new IllegalStateException(
                    "El plazo de la tarea venció el " + dueAt + ". " +
                    "Para registrar ejecución tardía utilice el estatus FAILED con los comentarios de causa.");
        }
        execution.setStatus(TaskStatus.COMPLETED);
        execution.setFinishedAt(now);
        execution.setOnTime(true);  // Garantizado: la validación anterior descarta now > dueAt
    }

    /**
     * IN_PROGRESS → FAILED
     * Registra el fallo; {@code onTime} siempre {@code false} por definición.
     * Los comentarios son obligatorios para garantizar trazabilidad operativa (Obj. #20).
     *
     * @throws IllegalStateException si no se proporcionan comentarios
     */
    private void applyFailed(Execution execution, Instant now, String comments) {
        if (comments == null || comments.isBlank()) {
            throw new IllegalStateException(
                    "Al marcar una tarea como FAILED debe proporcionar el campo 'comments' con la causa del fallo.");
        }
        execution.setStatus(TaskStatus.FAILED);
        execution.setFinishedAt(now);
        execution.setOnTime(false);
    }

    /**
     * FAILED → PENDING (re-apertura para rework).
     * Habilita el ciclo de re-trabajo medido por KPI #9 (Velocidad de Corrección).
     * El reworkCount ya fue incrementado al marcar FAILED; no se incrementa de nuevo.
     */
    private void applyReopened(Execution execution) {
        execution.setStatus(TaskStatus.PENDING);
        execution.setStartedAt(null);
        execution.setFinishedAt(null);
        execution.setOnTime(null);
    }

    // ── Notificaciones de estado ─────────────────────────────────────────

    /**
     * Emite el evento SSE correspondiente al nuevo estado de la tarea.
     *
     * <ul>
     *   <li>IN_PROGRESS → avisa a los gerentes/admins de la sucursal.</li>
     *   <li>COMPLETED   → avisa a los gerentes/admins de la sucursal.</li>
     *   <li>FAILED      → avisa a los gerentes/admins de la sucursal (severity: critical).</li>
     * </ul>
     */
    private void emitStatusNotification(Task task, TaskStatus newStatus) {
        String type;
        String severity;
        String title;

        switch (newStatus) {
            case IN_PROGRESS -> {
                type     = "TASK_STARTED";
                severity = "info";
                title    = "Tarea Iniciada";
            }
            case COMPLETED -> {
                type     = "TASK_COMPLETED";
                severity = "info";
                title    = "Tarea Completada";
            }
            case FAILED -> {
                type     = "TASK_FAILED";
                severity = "critical";
                title    = "Tarea Fallida";
            }
            case PENDING -> {
                type     = "TASK_REOPENED";
                severity = "warning";
                title    = "Tarea Reabierta";
            }
        }

        String comments = task.getComments();
        String body = task.getTitle() + " · " + task.getPosition()
                + (comments != null && !comments.isBlank()
                        ? " — " + comments.substring(0, Math.min(60, comments.length()))
                        : "");

        notificationService.sendToStoreManagers(task.getStoreId(), NotificationEvent.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .severity(severity)
                .title(title)
                .body(body)
                .taskId(task.getId())
                .storeId(task.getStoreId())
                .timestamp(Instant.now())
                .build());
    }

    // ── Evidencias ───────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     * <p>
     * Flujo:
     * <ol>
     *   <li>Resuelve el MongoDB _id del usuario autenticado.</li>
     *   <li>Valida que la tarea exista, esté activa y en estado {@code IN_PROGRESS}.</li>
     *   <li>Valida que el usuario sea el colaborador asignado.</li>
     *   <li>Sube el archivo a GCS vía {@link GcsService}.</li>
     *   <li>Agrega la URL al sub-documento {@code evidence} y persiste.</li>
     * </ol>
     */
    @Override
    public EvidenceUploadResponse addEvidence(String taskId, String mediaType,
                                               byte[] fileBytes, String contentType,
                                               String extension, String numeroUsuario) {
        User user = userRepository.findByNumeroUsuario(numeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado: " + numeroUsuario));

        Task task = taskRepository.findById(taskId)
                .filter(Task::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada: " + taskId));

        if (task.getExecution().getStatus() != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Las evidencias solo se pueden agregar a tareas en estado IN_PROGRESS. " +
                    "Estado actual: " + task.getExecution().getStatus());
        }

        if (!task.getAssignedUserId().equals(user.getId())) {
            throw new IllegalStateException(
                    "Solo el colaborador asignado puede agregar evidencias a esta tarea.");
        }

        String tipo = "IMAGE".equalsIgnoreCase(mediaType) ? "img" : "vid";
        String url  = gcsService.uploadFile(task.getStoreId(), taskId, tipo,
                                            fileBytes, contentType, extension);

        Evidence evidence = task.getExecution().getEvidence();
        if (evidence == null) {
            evidence = new Evidence();
            task.getExecution().setEvidence(evidence);
        }

        if ("IMAGE".equalsIgnoreCase(mediaType)) {
            evidence.getImages().add(url);
        } else {
            evidence.getVideos().add(url);
        }

        taskRepository.save(task);

        return EvidenceUploadResponse.builder()
                .taskId(taskId)
                .type(mediaType.toUpperCase())
                .url(url)
                .build();
    }

    // ── Mapper Task → TaskResponse ───────────────────────────────────────

    private TaskResponse toResponse(Task task) {
        Execution exec     = task.getExecution();
        Evidence  evidence = exec.getEvidence() != null ? exec.getEvidence() : new Evidence();

        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .category(task.getCategory())
                .critical(task.isCritical())
                .assignedUserId(task.getAssignedUserId())
                .position(task.getPosition())
                .storeId(task.getStoreId())
                .shift(task.getShift())
                .dueAt(task.getDueAt())
                .status(exec.getStatus())
                .startedAt(exec.getStartedAt())
                .finishedAt(exec.getFinishedAt())
                .onTime(exec.getOnTime())
                .evidenceImages(evidence.getImages())
                .evidenceVideos(evidence.getVideos())
                .reworkCount(task.getReworkCount())
                .qualityRating(task.getQualityRating())
                .comments(task.getComments())
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
