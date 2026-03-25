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
 * Transiciones de estado permitidas:
 * <ul>
 *   <li>PROGRAMADA  → EN_CURSO       : registra startedAt</li>
 *   <li>EN_CURSO    → COMPLETADA     : requiere grade; calcula passed y onTime</li>
 *   <li>EN_CURSO    → NO_COMPLETADA  : requiere comments; onTime = false</li>
 *   <li>COMPLETADA / NO_COMPLETADA   : estados terminales</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TrainingServiceImpl implements TrainingService {

    private final TrainingRepository         trainingRepository;
    private final TrainingMaterialRepository materialRepository;
    private final TrainingTemplateRepository templateRepository;
    private final TrainingMaterialService    materialService;
    private final TrainingTemplateService    templateService;
    private final UserRepository             userRepository;
    private final ApplicationEventPublisher  eventPublisher;

    // ── Crear ────────────────────────────────────────────────────────────

    @Override
    public TrainingResponse create(CreateTrainingRequest req, String createdBy) {
        // Buscar por ObjectId primero, fallback a numeroUsuario
        User assignedUser = userRepository.findById(req.getAssignedUserId())
                .or(() -> userRepository.findByNumeroUsuario(req.getAssignedUserId()))
                .filter(User::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario asignado no encontrado o inactivo: " + req.getAssignedUserId()));

        // Construir lista de materiales referenciados
        List<TrainingMaterialRef> materialRefs = buildMaterialRefs(req.getMaterialIds());

        Training training = Training.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .level(req.getLevel())
                .durationHours(req.getDurationHours())
                .minPassGrade(req.getMinPassGrade())
                .assignedUserId(req.getAssignedUserId())
                .position(assignedUser.getPuesto())
                .storeId(req.getStoreId())
                .shift(req.getShift())
                .dueAt(req.getDueAt())
                .templateId(req.getTemplateId())
                .materials(materialRefs)
                .category(req.getCategory())
                .tags(req.getTags() != null ? req.getTags() : new ArrayList<>())
                .progress(TrainingProgress.builder().build())
                .createdBy(createdBy)
                .activo(true)
                .build();

        Training saved = trainingRepository.save(training);

        // Incrementar usageCount de cada material asignado
        materialRefs.forEach(r -> materialService.incrementUsage(r.getMaterialId()));

        eventPublisher.publishEvent(new TrainingCreatedEvent(
                saved.getId(), saved.getAssignedUserId(),
                saved.getStoreId(), saved.getTitle(), saved.getShift()));

        return toResponse(saved);
    }

    // ── Consultas ────────────────────────────────────────────────────────

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
    public List<TrainingResponse> getByStore(String storeId) {
        return trainingRepository.findByStoreIdAndActivoTrue(storeId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public TrainingResponse getById(String id) {
        return trainingRepository.findById(id)
                .filter(Training::isActivo)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Capacitación no encontrada: " + id));
    }

    // ── Actualizar Progreso ──────────────────────────────────────────────

    @Override
    public TrainingResponse updateProgress(String id, UpdateTrainingProgressRequest req,
                                           String currentUser) {
        Training training = trainingRepository.findById(id)
                .filter(Training::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Capacitación no encontrada: " + id));

        TrainingProgress progress = training.getProgress();
        if (progress == null) {
            progress = TrainingProgress.builder().build();
            training.setProgress(progress);
        }
        TrainingStatus current    = progress.getStatus();
        TrainingStatus next       = req.getNewStatus();

        validateTransition(current, next);

        Instant now = Instant.now();

        switch (next) {
            case EN_CURSO -> applyEnCurso(progress, now, req.getPercentage());
            case COMPLETADA -> applyCompletada(progress, training.getDueAt(), now,
                    training.getMinPassGrade(), req.getGrade(), req.getPercentage());
            case NO_COMPLETADA -> applyNoCompletada(progress, now, req.getComments(),
                    req.getPercentage());
            default -> { /* PROGRAMADA no es destino válido desde otro estado */ }
        }

        Training saved = trainingRepository.save(training);

        eventPublisher.publishEvent(new TrainingProgressChangedEvent(
                saved.getId(), next, saved.getStoreId(),
                saved.getTitle(), saved.getPosition()));

        return toResponse(saved);
    }

    // ── Soft-delete ──────────────────────────────────────────────────────

    @Override
    public void deactivate(String id) {
        Training training = trainingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Capacitación no encontrada: " + id));
        training.setActivo(false);
        trainingRepository.save(training);
    }

    // ── Lógica de Transiciones ───────────────────────────────────────────

    private void validateTransition(TrainingStatus current, TrainingStatus next) {
        boolean valid = switch (current) {
            case PROGRAMADA    -> next == TrainingStatus.EN_CURSO;
            case EN_CURSO      -> next == TrainingStatus.COMPLETADA || next == TrainingStatus.NO_COMPLETADA
                                  || next == TrainingStatus.EN_CURSO; // permitir actualizar % en EN_CURSO
            case COMPLETADA    -> false;
            case NO_COMPLETADA -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    String.format("Transición inválida: %s → %s. " +
                            "Flujo permitido: PROGRAMADA→EN_CURSO, EN_CURSO→COMPLETADA|NO_COMPLETADA.",
                            current, next));
        }
    }

    /** PROGRAMADA → EN_CURSO: registra inicio; opcionalmente setea porcentaje. */
    private void applyEnCurso(TrainingProgress progress, Instant now, Integer percentage) {
        if (progress.getStatus() == TrainingStatus.PROGRAMADA) {
            progress.setStartedAt(now);
        }
        progress.setStatus(TrainingStatus.EN_CURSO);
        if (percentage != null) {
            progress.setPercentage(percentage);
        }
    }

    /**
     * EN_CURSO → COMPLETADA.
     * Requiere grade. Calcula passed y onTime.
     */
    private void applyCompletada(TrainingProgress progress, Instant dueAt, Instant now,
                                  double minPassGrade, Double grade, Integer percentage) {
        if (grade == null) {
            throw new IllegalStateException(
                    "Al marcar una capacitación como COMPLETADA debe proporcionar el campo 'grade' (0–10).");
        }
        progress.setStatus(TrainingStatus.COMPLETADA);
        progress.setCompletedAt(now);
        progress.setGrade(grade);
        progress.setPassed(grade >= minPassGrade);
        progress.setOnTime(!now.isAfter(dueAt));
        if (percentage != null) {
            progress.setPercentage(percentage);
        } else {
            progress.setPercentage(100);
        }
    }

    /**
     * EN_CURSO → NO_COMPLETADA.
     * Requiere comments. onTime siempre false.
     */
    private void applyNoCompletada(TrainingProgress progress, Instant now,
                                    String comments, Integer percentage) {
        if (comments == null || comments.isBlank()) {
            throw new IllegalStateException(
                    "Al marcar una capacitación como NO_COMPLETADA debe proporcionar el campo 'comments' con la causa.");
        }
        progress.setStatus(TrainingStatus.NO_COMPLETADA);
        progress.setCompletedAt(now);
        progress.setOnTime(false);
        progress.setComments(comments);
        if (percentage != null) {
            progress.setPercentage(percentage);
        }
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
    public Page<TrainingResponse> getByStorePaged(String storeId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return trainingRepository.findByStoreIdAndActivoTrue(storeId, pageable)
                .map(this::toResponse);
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
                                               String createdBy) {
        TrainingTemplate template = templateRepository.findById(templateId)
                .filter(TrainingTemplate::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla no encontrada: " + templateId));

        User assignedUser = userRepository.findById(assignedUserId)
                .or(() -> userRepository.findByNumeroUsuario(assignedUserId))
                .filter(User::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario asignado no encontrado o inactivo: " + assignedUserId));

        // Copiar materiales de la plantilla como refs de training
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
                .position(assignedUser.getPuesto())
                .storeId(storeId)
                .shift(shift)
                .dueAt(dueAt)
                .templateId(templateId)
                .materials(materialRefs)
                .category(template.getCategory())
                .tags(new ArrayList<>(template.getTags()))
                .progress(TrainingProgress.builder().build())
                .createdBy(createdBy)
                .activo(true)
                .build();

        Training saved = trainingRepository.save(training);

        // Métricas: usageCount en materiales + timesUsed en plantilla
        materialRefs.forEach(r -> materialService.incrementUsage(r.getMaterialId()));
        templateService.incrementTimesUsed(templateId);

        eventPublisher.publishEvent(new TrainingCreatedEvent(
                saved.getId(), saved.getAssignedUserId(),
                saved.getStoreId(), saved.getTitle(), saved.getShift()));

        return toResponse(saved);
    }

    // ── Marcar material como visto ────────────────────────────────────────

    @Override
    public TrainingResponse markMaterialViewed(String trainingId, String materialId,
                                               String currentUser) {
        Training training = trainingRepository.findById(trainingId)
                .filter(Training::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Capacitación no encontrada: " + trainingId));

        training.getMaterials().stream()
                .filter(r -> r.getMaterialId().equals(materialId))
                .findFirst()
                .ifPresent(r -> {
                    r.setViewed(true);
                    r.setViewedAt(Instant.now());
                });

        return toResponse(trainingRepository.save(training));
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

    /** Resuelve materiales en lote (1 query) para evitar N+1. */
    private Map<String, TrainingMaterial> resolveMaterialMap(List<TrainingMaterialRef> refs) {
        if (refs == null || refs.isEmpty()) return Map.of();
        List<String> ids = refs.stream().map(TrainingMaterialRef::getMaterialId).toList();
        return materialRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(TrainingMaterial::getId, Function.identity()));
    }

    // ── Mapper Training → TrainingResponse ──────────────────────────────

    private TrainingResponse toResponse(Training t) {
        TrainingProgress p = t.getProgress() != null ? t.getProgress() : TrainingProgress.builder().build();

        // Resolver materiales en 1 query
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
                .position(t.getPosition())
                .storeId(t.getStoreId())
                .shift(t.getShift())
                .dueAt(t.getDueAt())
                .templateId(t.getTemplateId())
                .materials(resolvedMaterials)
                .category(t.getCategory())
                .tags(t.getTags())
                .status(p.getStatus())
                .startedAt(p.getStartedAt())
                .completedAt(p.getCompletedAt())
                .onTime(p.getOnTime())
                .percentage(p.getPercentage())
                .grade(p.getGrade())
                .passed(p.getPassed())
                .comments(p.getComments())
                .createdBy(t.getCreatedBy())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
