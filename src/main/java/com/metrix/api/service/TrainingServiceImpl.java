package com.metrix.api.service;

import com.metrix.api.dto.*;
import com.metrix.api.event.DomainEvents.TrainingCreatedEvent;
import com.metrix.api.event.DomainEvents.TrainingProgressChangedEvent;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.*;
import com.metrix.api.repository.TrainingMaterialRepository;
import com.metrix.api.repository.TrainingRepository;
import com.metrix.api.repository.TrainingTemplateRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementación del motor de capacitaciones para METRIX (Sprint 10).
 * <p>
 * Arquitectura refactorizada:
 * <ul>
 *   <li>{@link TrainingStateMachine} — fuente única de transiciones de estado</li>
 *   <li>{@link RolePolicy} — fuente única de reglas de asignación por rol</li>
 *   <li>GETs son PUROS — no mutan ni persisten DB (CQRS ligero)</li>
 *   <li>Porcentaje se calcula on-read en toResponse() sin side-effects</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TrainingServiceImpl implements TrainingService {
    private static final double DEFAULT_MIN_PASS_GRADE = 7.0;
    private static final int DEFAULT_DURATION_HOURS = 1;

    private final TrainingRepository         trainingRepository;
    private final TrainingMaterialRepository materialRepository;
    private final TrainingTemplateRepository templateRepository;
    private final TrainingMaterialService    materialService;
    private final TrainingTemplateService    templateService;
    private final UserRepository             userRepository;
    private final ApplicationEventPublisher  eventPublisher;
    private final TrainingStateMachine       stateMachine;
    private final RolePolicy                 rolePolicy;

    // ── Crear ────────────────────────────────────────────────────────────

    @Override
    public TrainingResponse create(CreateTrainingRequest req, String createdBy) {
        User creator = resolveUser(createdBy);
        User assignedUser = resolveUser(req.getAssignedUserId());
        rolePolicy.validateAssignment(creator, assignedUser, req.getStoreId());

        List<TrainingMaterialRef> materialRefs = buildMaterialRefs(req.getMaterialIds());

        Training training = Training.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .level(req.getLevel())
                .durationHours(DEFAULT_DURATION_HOURS)
                .minPassGrade(DEFAULT_MIN_PASS_GRADE)
                .assignedUserId(assignedUser.getId())
                .assignedUserName(assignedUser.getNombre())
                .position(assignedUser.getPuesto())
                .storeId(req.getStoreId())
                .shift(req.getShift())
                .dueAt(req.getDueAt())
                .templateId(req.getTemplateId())
                .assignmentGroupId(req.getAssignmentGroupId())
                .materials(materialRefs)
                .category(req.getCategory())
                .tags(req.getTags() != null ? req.getTags() : new ArrayList<>())
                .progress(TrainingProgress.builder().build())
                .createdBy(createdBy)
                .activo(true)
                .build();

        Training saved = trainingRepository.save(training);

        materialRefs.forEach(r -> materialService.incrementUsage(r.getMaterialId()));

        eventPublisher.publishEvent(new TrainingCreatedEvent(
                saved.getId(), saved.getAssignedUserId(),
                saved.getStoreId(), saved.getTitle(), saved.getShift()));

        return toResponse(saved);
    }

    // ── Consultas (PURAS — no mutan DB) ──────────────────────────────────

    @Override
    public List<TrainingResponse> getMyTrainings(String userId) {
        return trainingRepository.findByAssignedUserIdAndActivoTrue(userId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<TrainingResponse> getAll() {
        return trainingRepository.findByActivoTrue()
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<TrainingResponse> getByStore(String storeId, String callerIdentifier) {
        User caller = resolveUser(callerIdentifier);
        List<Training> all = isScopedManager(caller)
                ? trainingRepository.findByStoreIdAndCreatedByInAndActivoTrue(
                        storeId, createdByKeys(caller))
                : trainingRepository.findByStoreIdAndActivoTrue(storeId);

        return all.stream().map(this::toResponse).toList();
    }

    @Override
    public List<TrainingResponse> getByAssignmentGroupId(String assignmentGroupId,
                                                         String callerIdentifier) {
        User caller = resolveUser(callerIdentifier);
        return trainingRepository.findByAssignmentGroupIdAndActivoTrue(assignmentGroupId)
                .stream()
                .filter(training -> canView(training, caller))
                .sorted(Comparator.comparing(Training::getAssignedUserName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toResponse)
                .toList();
    }

    @Override
    public TrainingResponse getById(String id, String callerIdentifier) {
        Training training = trainingRepository.findById(id)
                .filter(Training::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Capacitación no encontrada: " + id));
        ensureVisible(training, callerIdentifier);
        return toResponse(training);
    }

    // ── Actualizar Progreso (via StateMachine) ──────────────────────────

    @Override
    public TrainingResponse update(String id, UpdateTrainingRequest req, String callerIdentifier) {
        Training target = trainingRepository.findById(id)
                .filter(Training::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Capacitacion no encontrada: " + id));
        ensureTrainingEditable(target);
        ensureOwnership(target, callerIdentifier);

        String groupId = target.getAssignmentGroupId();
        if (groupId != null && !groupId.isBlank()) {
            List<Training> grouped = trainingRepository.findByAssignmentGroupIdAndActivoTrue(groupId);
            if (!grouped.isEmpty()) {
                grouped.forEach(this::ensureTrainingEditable);
                grouped.forEach(training -> ensureOwnership(training, callerIdentifier));
                grouped.forEach(training -> applyEditableFields(training, req));
                List<Training> saved = trainingRepository.saveAll(grouped);
                return saved.stream()
                        .filter(training -> training.getId().equals(id))
                        .findFirst()
                        .map(this::toResponse)
                        .orElseGet(() -> toResponse(saved.get(0)));
            }
        }

        applyEditableFields(target, req);
        return toResponse(trainingRepository.save(target));
    }

    @Override
    public TrainingResponse updateProgress(String id, UpdateTrainingProgressRequest req,
                                           String currentUser) {
        Training training = trainingRepository.findById(id)
                .filter(Training::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Capacitación no encontrada: " + id));

        User operator = resolveUser(currentUser);
        rolePolicy.validateOperationScope(operator, training.getAssignedUserId(), training.getStoreId());

        stateMachine.transitionByCommand(training, req.getNewStatus(),
                req.getGrade(), req.getComments(), req.getPercentage());

        Training saved = trainingRepository.save(training);

        eventPublisher.publishEvent(new TrainingProgressChangedEvent(
                saved.getId(), req.getNewStatus(), saved.getStoreId(),
                saved.getTitle(), saved.getPosition()));

        return toResponse(saved);
    }

    // ── Soft-delete ──────────────────────────────────────────────────────

    @Override
    public void deactivate(String id, String callerIdentifier) {
        Training training = trainingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Capacitación no encontrada: " + id));
        ensureTrainingDeletable(training);
        ensureOwnership(training, callerIdentifier);
        String groupId = training.getAssignmentGroupId();
        if (groupId != null && !groupId.isBlank()) {
            List<Training> grouped = trainingRepository.findByAssignmentGroupIdAndActivoTrue(groupId);
            if (!grouped.isEmpty()) {
                grouped.forEach(this::ensureTrainingDeletable);
                grouped.forEach(item -> ensureOwnership(item, callerIdentifier));
                grouped.forEach(item -> item.setActivo(false));
                trainingRepository.saveAll(grouped);
                return;
            }
        }

        training.setActivo(false);
        trainingRepository.save(training);
    }

    // ── Consultas paginadas ───────────────────────────────────────────────

    @Override
    public Page<TrainingResponse> getMyTrainingsPaged(String userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return trainingRepository.findByAssignedUserIdAndActivoTrue(userId, pageable)
                .map(this::toResponse);
    }

    @Override
    public Page<TrainingResponse> getByStorePaged(String storeId, int page, int size,
                                                  String callerIdentifier) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        User caller = resolveUser(callerIdentifier);
        Page<Training> trainings = isScopedManager(caller)
                ? trainingRepository.findByStoreIdAndCreatedByInAndActivoTrue(
                        storeId, createdByKeys(caller), pageable)
                : trainingRepository.findByStoreIdAndActivoTrue(storeId, pageable);
        return trainings.map(this::toResponse);
    }

    @Override
    public Page<TrainingResponse> getAllPaged(int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return trainingRepository.findByActivoTrue(pageable)
                .map(this::toResponse);
    }

    // ── Crear desde plantilla ─────────────────────────────────────────────

    @Override
    public TrainingResponse createFromTemplate(String templateId, String assignedUserId,
                                               String storeId, String shift, Instant dueAt,
                                               String assignmentGroupId,
                                               String createdBy) {
        User creator = resolveUser(createdBy);

        TrainingTemplate template = templateRepository.findById(templateId)
                .filter(TrainingTemplate::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla no encontrada: " + templateId));

        User assignedUser = resolveUser(assignedUserId);
        rolePolicy.validateAssignment(creator, assignedUser, storeId);

        List<TrainingMaterialRef> materialRefs = template.getMaterials().stream()
                .map(tm -> TrainingMaterialRef.builder()
                        .materialId(tm.getMaterialId())
                        .order(tm.getOrder())
                        .required(tm.isRequired())
                        .notes(tm.getNotes())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));

        Training training = Training.builder()
                .title(template.getTitle())
                .description(template.getDescription())
                .level(template.getLevel())
                .durationHours(template.getDurationHours())
                .minPassGrade(template.getMinPassGrade())
                .assignedUserId(assignedUser.getId())
                .assignedUserName(assignedUser.getNombre())
                .position(assignedUser.getPuesto())
                .storeId(storeId)
                .shift(shift)
                .dueAt(dueAt)
                .templateId(templateId)
                .assignmentGroupId(assignmentGroupId)
                .materials(materialRefs)
                .category(template.getCategory())
                .tags(new ArrayList<>(template.getTags()))
                .progress(TrainingProgress.builder().build())
                .createdBy(createdBy)
                .activo(true)
                .build();

        Training saved = trainingRepository.save(training);

        materialRefs.forEach(r -> materialService.incrementUsage(r.getMaterialId()));
        templateService.incrementTimesUsed(templateId);

        eventPublisher.publishEvent(new TrainingCreatedEvent(
                saved.getId(), saved.getAssignedUserId(),
                saved.getStoreId(), saved.getTitle(), saved.getShift()));

        return toResponse(saved);
    }

    // ── Marcar material como visto (via StateMachine) ────────────────────

    @Override
    public TrainingResponse markMaterialViewed(String trainingId, String materialId,
                                               String currentUser) {
        Training training = trainingRepository.findById(trainingId)
                .filter(Training::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Capacitación no encontrada: " + trainingId));

        User operator = resolveUser(currentUser);
        rolePolicy.validateOperationScope(operator, training.getAssignedUserId(), training.getStoreId());

        Instant now = Instant.now();
        training.getMaterials().stream()
                .filter(r -> r.getMaterialId().equals(materialId))
                .findFirst()
                .ifPresent(r -> {
                    r.setViewed(true);
                    r.setViewedAt(now);
                });

        boolean statusChanged = stateMachine.tryAutoCompleteByMaterials(training);

        Training saved = trainingRepository.save(training);

        if (statusChanged) {
            eventPublisher.publishEvent(new TrainingProgressChangedEvent(
                    saved.getId(), saved.getProgress().getStatus(),
                    saved.getStoreId(), saved.getTitle(), saved.getPosition()));
        }

        return toResponse(saved);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private List<TrainingMaterialRef> buildMaterialRefs(List<String> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) return new ArrayList<>();
        List<TrainingMaterialRef> refs = new ArrayList<>();
        for (int i = 0; i < materialIds.size(); i++) {
            refs.add(TrainingMaterialRef.builder()
                    .materialId(materialIds.get(i))
                    .order(i + 1)
                    .required(true)
                    .build());
        }
        return refs;
    }

    private void applyEditableFields(Training training, UpdateTrainingRequest req) {
        training.setTitle(req.getTitle());
        training.setDescription(req.getDescription());
        training.setLevel(req.getLevel());
        training.setStoreId(req.getStoreId());
        training.setShift(req.getShift());
        training.setDueAt(req.getDueAt());
    }

    private void ensureOwnership(Training training, String callerIdentifier) {
        User caller = resolveUser(callerIdentifier);
        if (hasRole(caller, Role.ADMIN)) return; // ADMIN can operate on any training
        if (!isCreatedBy(training, caller)) {
            throw new IllegalStateException(
                    "Solo puedes modificar capacitaciones que tú creaste.");
        }
    }

    private void ensureVisible(Training training, String callerIdentifier) {
        User caller = resolveUser(callerIdentifier);
        if (!canView(training, caller)) {
            throw new AccessDeniedException("No puedes ver esta capacitaciÃ³n.");
        }
    }

    private boolean canView(Training training, User caller) {
        if (hasRole(caller, Role.ADMIN)) return true;
        if (Objects.equals(caller.getId(), training.getAssignedUserId())) return true;
        return isScopedManager(caller) && isCreatedBy(training, caller);
    }

    private boolean isScopedManager(User user) {
        return hasRole(user, Role.GERENTE) && !hasRole(user, Role.ADMIN);
    }

    private boolean isCreatedBy(Training training, User caller) {
        String createdBy = training.getCreatedBy();
        return createdBy != null
                && (Objects.equals(caller.getId(), createdBy)
                || Objects.equals(caller.getNumeroUsuario(), createdBy));
    }

    private List<String> createdByKeys(User caller) {
        return java.util.stream.Stream.of(caller.getId(), caller.getNumeroUsuario())
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private void ensureTrainingEditable(Training training) {
        TrainingStatus status = currentStatus(training);
        if (status == TrainingStatus.COMPLETADA) {
            throw new IllegalStateException(
                    "No se puede editar una capacitación en estado COMPLETADA.");
        }
    }

    private void ensureTrainingDeletable(Training training) {
        TrainingStatus status = currentStatus(training);
        if (status == TrainingStatus.COMPLETADA) {
            throw new IllegalStateException(
                    "No se puede eliminar una capacitación en estado COMPLETADA.");
        }
    }

    private TrainingStatus currentStatus(Training training) {
        TrainingProgress progress = training.getProgress();
        if (progress == null || progress.getStatus() == null) {
            return TrainingStatus.PROGRAMADA;
        }
        return progress.getStatus();
    }

    private boolean hasRole(User user, Role role) {
        return user.getRoles() != null && user.getRoles().contains(role);
    }

    /** Resuelve materiales en lote (1 query) para evitar N+1. */
    private Map<String, TrainingMaterial> resolveMaterialMap(List<TrainingMaterialRef> refs) {
        if (refs == null || refs.isEmpty()) return Map.of();
        List<String> ids = refs.stream().map(TrainingMaterialRef::getMaterialId).toList();
        return materialRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(TrainingMaterial::getId, Function.identity()));
    }

    // ── Mapper Training → TrainingResponse (PURO — sin side-effects) ────

    private TrainingResponse toResponse(Training t) {
        TrainingProgress p = t.getProgress() != null ? t.getProgress() : TrainingProgress.builder().build();

        // Calcular porcentaje on-read sin persistir
        int computedPercentage = stateMachine.computePercentage(t);

        Map<String, TrainingMaterial> materialMap = resolveMaterialMap(t.getMaterials());

        List<TrainingMaterialRefResponse> resolvedMaterials = (t.getMaterials() == null)
                ? List.of()
                : t.getMaterials().stream()
                        .map(ref -> {
                            TrainingMaterial m = materialMap.get(ref.getMaterialId());
                            var b = TrainingMaterialRefResponse.builder()
                                    .materialId(ref.getMaterialId())
                                    .order(ref.getOrder())
                                    .required(ref.isRequired())
                                    .notes(ref.getNotes())
                                    .viewed(ref.isViewed())
                                    .viewedAt(ref.getViewedAt());
                            if (m != null) {
                                b.title(m.getTitle())
                                 .description(m.getDescription())
                                 .type(m.getType())
                                 .url(m.getUrl())
                                 .originalFileName(m.getOriginalFileName())
                                 .fileSizeBytes(m.getFileSizeBytes())
                                 .mimeType(m.getMimeType())
                                 .category(m.getCategory())
                                 .tags(m.getTags());
                            }
                            return b.build();
                        })
                        .toList();

        return TrainingResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .level(t.getLevel())
                .durationHours(t.getDurationHours())
                .minPassGrade(t.getMinPassGrade())
                .assignedUserId(t.getAssignedUserId())
                .assignedUserName(t.getAssignedUserName())
                .position(t.getPosition())
                .storeId(t.getStoreId())
                .shift(t.getShift())
                .dueAt(t.getDueAt())
                .assignmentGroupId(t.getAssignmentGroupId())
                .templateId(t.getTemplateId())
                .materials(resolvedMaterials)
                .category(t.getCategory())
                .tags(t.getTags())
                .status(p.getStatus())
                .startedAt(p.getStartedAt())
                .completedAt(p.getCompletedAt())
                .onTime(p.getOnTime())
                .percentage(computedPercentage)
                .grade(p.getGrade())
                .passed(p.getPassed())
                .comments(p.getComments())
                .createdBy(t.getCreatedBy())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    /**
     * Resuelve un usuario por ID o por numeroUsuario.
     * Orden unificado: siempre findById primero, luego findByNumeroUsuario.
     */
    private User resolveUser(String identifier) {
        return userRepository.findById(identifier)
                .or(() -> userRepository.findByNumeroUsuario(identifier))
                .filter(User::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado o inactivo: " + identifier));
    }
}
