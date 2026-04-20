package com.metrix.api.service;

import com.metrix.api.dto.CreateTaskRequest;
import com.metrix.api.dto.EvidenceUploadResponse;
import com.metrix.api.dto.ProcessStepRequest;
import com.metrix.api.dto.ProcessStepResponse;
import com.metrix.api.dto.QualityRatingRequest;
import com.metrix.api.dto.TaskResponse;
import com.metrix.api.dto.UpdateStatusRequest;
import com.metrix.api.event.DomainEvents.TaskCreatedEvent;
import com.metrix.api.event.DomainEvents.TaskStatusChangedEvent;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.*;
import com.metrix.api.repository.TaskEvidenceRepository;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository            taskRepository;
    private final TaskEvidenceRepository   taskEvidenceRepository;
    private final UserRepository           userRepository;
    private final GcsService               gcsService;
    private final ApplicationEventPublisher eventPublisher;

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
        // Buscar por ObjectId primero, fallback a numeroUsuario
        User assignedUser = userRepository.findById(request.getAssignedUserId())
                .or(() -> userRepository.findByNumeroUsuario(request.getAssignedUserId()))
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
                .processes(mapProcessSteps(request.getProcesses()))
                .recurring(request.isRecurring())
                .recurrenceDays(request.getRecurrenceDays() != null ? request.getRecurrenceDays() : List.of())
                .recurrenceStartTime(request.getRecurrenceStartTime())
                .recurrenceEndTime(request.getRecurrenceEndTime())
                .execution(Execution.builder()
                        .status(TaskStatus.PENDING)
                        .evidence(new Evidence())
                        .build())
                .createdBy(createdBy)
                .activo(true)
                .build();

        Task saved = taskRepository.save(task);
        log.info("[AUDIT] Task created: id={}, title='{}', assignee={}, store={}, createdBy={}",
                saved.getId(), saved.getTitle(), saved.getAssignedUserId(), saved.getStoreId(), createdBy);

        // Emit domain event — TaskEventListener handles SSE notification
        eventPublisher.publishEvent(new TaskCreatedEvent(
                saved.getId(), saved.getAssignedUserId(),
                saved.getStoreId(), saved.getTitle(), saved.getShift()));

        return toResponse(saved, assignedUser.getNombre());
    }

    // ── Consultas ────────────────────────────────────────────────────────

    @Override
    public List<TaskResponse> getTasksByUser(String assignedUserId) {
        List<Task> tasks = taskRepository.findByAssignedUserIdAndActivoTrue(assignedUserId);
        return toResponseList(tasks);
    }

    @Override
    public List<TaskResponse> getTasksByUserAndShift(String assignedUserId, String shift) {
        List<Task> tasks = taskRepository.findByAssignedUserIdAndShiftAndActivoTrue(assignedUserId, shift);
        return toResponseList(tasks);
    }

    @Override
    public List<TaskResponse> getTasksByStore(String storeId) {
        List<Task> tasks = taskRepository.findByStoreIdAndActivoTrue(storeId);
        return toResponseList(tasks);
    }

    @Override
    public List<TaskResponse> getTasksByStoreAndShift(String storeId, String shift) {
        List<Task> tasks = taskRepository.findByStoreIdAndShiftAndActivoTrue(storeId, shift);
        return toResponseList(tasks);
    }

    @Override
    public TaskResponse getById(String taskId) {
        return taskRepository.findById(taskId)
                .filter(Task::isActivo)
                .map(t -> toResponse(t, resolveUserName(t.getAssignedUserId())))
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
        User actor = userRepository.findByNumeroUsuario(currentUser)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado: " + currentUser));

        Execution execution = task.getExecution();
        TaskStatus current  = execution.getStatus();
        TaskStatus next     = request.getNewStatus();

        validateTransition(current, next);
        validateActorCanChangeStatus(task, actor, current, next);

        Instant now = Instant.now();

        switch (next) {
            case IN_PROGRESS -> applyInProgress(execution, now);
            case COMPLETED   -> applyCompleted(task, execution, task.getDueAt(), now);
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

        Task saved = taskRepository.save(task);
        log.info("[AUDIT] Task status changed: id={}, {}→{}, user={}, reworkCount={}",
                taskId, current, next, currentUser, saved.getReworkCount());

        // Emit domain event — TaskEventListener handles SSE notification
        eventPublisher.publishEvent(new TaskStatusChangedEvent(
                saved.getId(), current, next, saved.getStoreId(),
                saved.getAssignedUserId(), resolveUserName(saved.getAssignedUserId()),
                saved.getTitle(), saved.getPosition(), saved.getComments()));

        return toResponse(saved, resolveUserName(saved.getAssignedUserId()));
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
    private void applyCompleted(Task task, Execution execution, Instant dueAt, Instant now) {
        if (now.isAfter(dueAt)) {
            throw new IllegalStateException(
                    "El plazo de la tarea venció el " + dueAt + ". " +
                    "Para registrar ejecución tardía utilice el estatus FAILED con los comentarios de causa.");
        }
        validateCompletionRequirements(task, execution);
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

    private void validateActorCanChangeStatus(Task task, User actor, TaskStatus current, TaskStatus next) {
        boolean isAssignee = task.getAssignedUserId() != null && task.getAssignedUserId().equals(actor.getId());
        boolean isManager = actor.getRoles() != null
                && (actor.getRoles().contains(Role.ADMIN) || actor.getRoles().contains(Role.GERENTE));

        boolean allowed = switch (current) {
            case PENDING, IN_PROGRESS -> isAssignee;
            case FAILED -> next == TaskStatus.PENDING && isManager;
            case COMPLETED -> false;
        };

        if (!allowed) {
            throw new IllegalStateException(
                    "No tienes permisos para cambiar el estado de esta tarea. " +
                    "Solo el ejecutador asignado puede iniciarla o cerrarla, y solo GERENTE/ADMIN puede reabrirla.");
        }
    }

    private void validateCompletionRequirements(Task task, Execution execution) {
        Evidence evidence = execution.getEvidence() != null ? execution.getEvidence() : new Evidence();
        boolean hasEvidence = !evidence.getImages().isEmpty() || !evidence.getVideos().isEmpty();
        if (!hasEvidence) {
            throw new IllegalStateException(
                    "No puedes marcar la tarea como COMPLETED sin subir al menos una evidencia fotogrÃ¡fica o de video.");
        }

        boolean hasProcesses = task.getProcesses() != null && !task.getProcesses().isEmpty();
        boolean hasPendingProcesses = hasProcesses && task.getProcesses().stream().anyMatch(step -> !step.isCompleted());
        if (hasPendingProcesses) {
            throw new IllegalStateException(
                    "No puedes marcar la tarea como COMPLETED mientras existan procesos pendientes por completar.");
        }
    }

    // ── Calificación de Calidad (Sprint 18) ─────────────────────────────

    @Override
    public TaskResponse rateQuality(String taskId, QualityRatingRequest request, String currentUser) {
        Task task = taskRepository.findById(taskId)
                .filter(Task::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada: " + taskId));

        if (task.getExecution().getStatus() != TaskStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Solo se puede calificar la calidad de tareas en estado COMPLETED. " +
                    "Estado actual: " + task.getExecution().getStatus());
        }

        task.setQualityRating(request.getRating());
        if (request.getComments() != null && !request.getComments().isBlank()) {
            task.setComments(request.getComments());
        }

        Task saved = taskRepository.save(task);
        log.info("[AUDIT] Quality rated: taskId={}, rating={}, ratedBy={}",
                taskId, request.getRating(), currentUser);
        return toResponse(saved, resolveUserName(saved.getAssignedUserId()));
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

        // Save to separate collection (new architecture)
        taskEvidenceRepository.save(TaskEvidence.builder()
                .taskId(taskId)
                .storeId(task.getStoreId())
                .type(mediaType.toUpperCase())
                .url(url)
                .uploadedBy(numeroUsuario)
                .build());

        // Also update embedded evidence for backward compatibility
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

    @Override
    public TaskResponse updateProcessStep(String taskId, String stepId, boolean completed, String notes) {
        Task task = taskRepository.findById(taskId)
                .filter(Task::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada: " + taskId));

        boolean found = false;
        for (ProcessStep step : task.getProcesses()) {
            if (step.getStepId().equals(stepId)) {
                step.setCompleted(completed);
                step.setCompletedAt(completed ? java.time.Instant.now() : null);
                step.setNotes(notes);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new ResourceNotFoundException("Paso de proceso no encontrado: " + stepId);
        }

        Task saved = taskRepository.save(task);
        return toResponse(saved, resolveUserName(saved.getAssignedUserId()));
    }

    @Override
    public TaskResponse editProcessStep(String taskId, String stepId, String title, String description) {
        Task task = taskRepository.findById(taskId)
                .filter(Task::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada: " + taskId));

        for (ProcessStep step : task.getProcesses()) {
            if (step.getStepId().equals(stepId)) {
                step.setTitle(title);
                step.setDescription(description);
                break;
            }
        }
        Task saved = taskRepository.save(task);
        return toResponse(saved, resolveUserName(saved.getAssignedUserId()));
    }

    @Override
    public TaskResponse deleteProcessStep(String taskId, String stepId) {
        Task task = taskRepository.findById(taskId)
                .filter(Task::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada: " + taskId));

        task.getProcesses().removeIf(step -> step.getStepId().equals(stepId));
        Task saved = taskRepository.save(task);
        return toResponse(saved, resolveUserName(saved.getAssignedUserId()));
    }

    @Override
    public void deactivateTask(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarea no encontrada: " + taskId));
        task.setActivo(false);
        taskRepository.save(task);
        log.info("[AUDIT] Task deactivated: id={}", taskId);
    }

    @Override
    public List<TaskResponse> getAllTasks() {
        List<Task> tasks = taskRepository.findByActivoTrue();
        return toResponseList(tasks);
    }

    // ── Paginated queries ────────────────────────────────────────────────

    @Override
    public Page<TaskResponse> getTasksByStore(String storeId, Pageable pageable) {
        Page<Task> page = taskRepository.findByStoreIdAndActivoTrue(storeId, pageable);
        Map<String, String> names = batchResolveUserNames(page.getContent());
        return page.map(t -> toResponse(t, names.getOrDefault(t.getAssignedUserId(), t.getPosition())));
    }

    @Override
    public Page<TaskResponse> getTasksByUser(String assignedUserId, Pageable pageable) {
        Page<Task> page = taskRepository.findByAssignedUserIdAndActivoTrue(assignedUserId, pageable);
        Map<String, String> names = batchResolveUserNames(page.getContent());
        return page.map(t -> toResponse(t, names.getOrDefault(t.getAssignedUserId(), t.getPosition())));
    }

    @Override
    public Page<TaskResponse> getAllTasks(Pageable pageable) {
        Page<Task> page = taskRepository.findByActivoTrue(pageable);
        Map<String, String> names = batchResolveUserNames(page.getContent());
        return page.map(t -> toResponse(t, names.getOrDefault(t.getAssignedUserId(), t.getPosition())));
    }

    @Override
    public void deleteAll() {
        taskRepository.deleteAll();
        log.info("[AUDIT] All tasks purged by admin");
    }

    // ── Process Steps mappers ────────────────────────────────────────────

    private List<ProcessStep> mapProcessSteps(List<ProcessStepRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        List<ProcessStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            ProcessStepRequest r = requests.get(i);
            steps.add(ProcessStep.builder()
                    .stepId(java.util.UUID.randomUUID().toString().substring(0, 8))
                    .title(r.getTitle())
                    .description(r.getDescription())
                    .tags(r.getTags() != null ? r.getTags() : List.of())
                    .completed(false)
                    .order(i)
                    .build());
        }
        return steps;
    }

    private List<ProcessStepResponse> mapProcessStepsToResponse(List<ProcessStep> steps) {
        if (steps == null || steps.isEmpty()) return List.of();
        return steps.stream().map(s -> ProcessStepResponse.builder()
                .stepId(s.getStepId())
                .title(s.getTitle())
                .description(s.getDescription())
                .tags(s.getTags() != null ? s.getTags() : List.of())
                .completed(s.isCompleted())
                .completedAt(s.getCompletedAt())
                .notes(s.getNotes())
                .order(s.getOrder())
                .build()).toList();
    }

    // ── Batch user name resolution (eliminates N+1) ─────────────────────

    /** Resolves user names in 1 query for an entire list of tasks. */
    private Map<String, String> batchResolveUserNames(List<Task> tasks) {
        Set<String> userIds = tasks.stream()
                .map(Task::getAssignedUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNombre, (a, b) -> a));
    }

    /** Single user name resolve for detail endpoints. */
    private String resolveUserName(String userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(User::getNombre)
                .orElse(null);
    }

    /** Converts a list of tasks with batch-resolved names (eliminates N+1). */
    private List<TaskResponse> toResponseList(List<Task> tasks) {
        Map<String, String> names = batchResolveUserNames(tasks);
        return tasks.stream()
                .map(t -> toResponse(t, names.getOrDefault(t.getAssignedUserId(), t.getPosition())))
                .toList();
    }

    // ── Mapper Task → TaskResponse ───────────────────────────────────────

    private TaskResponse toResponse(Task task, String assignedName) {
        Execution exec     = task.getExecution();
        Evidence  evidence = exec.getEvidence() != null ? exec.getEvidence() : new Evidence();

        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .category(task.getCategory())
                .critical(task.isCritical())
                .assignedUserId(task.getAssignedUserId())
                .assignedUserName(assignedName != null ? assignedName : task.getPosition())
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
                .processes(mapProcessStepsToResponse(task.getProcesses()))
                .recurring(task.isRecurring())
                .recurrenceDays(task.getRecurrenceDays() != null ? task.getRecurrenceDays() : List.of())
                .recurrenceStartTime(task.getRecurrenceStartTime())
                .recurrenceEndTime(task.getRecurrenceEndTime())
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
