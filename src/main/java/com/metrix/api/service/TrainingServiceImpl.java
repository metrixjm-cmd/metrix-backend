package com.metrix.api.service;

import com.metrix.api.dto.CreateTrainingRequest;
import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.dto.TrainingResponse;
import com.metrix.api.dto.UpdateTrainingProgressRequest;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.*;
import com.metrix.api.repository.TrainingRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    private final TrainingRepository    trainingRepository;
    private final UserRepository        userRepository;
    private final NotificationService   notificationService;

    // ── Crear ────────────────────────────────────────────────────────────

    @Override
    public TrainingResponse create(CreateTrainingRequest req, String createdBy) {
        User assignedUser = userRepository.findById(req.getAssignedUserId())
                .filter(User::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario asignado no encontrado o inactivo: " + req.getAssignedUserId()));

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
                .progress(TrainingProgress.builder().build())
                .createdBy(createdBy)
                .activo(true)
                .build();

        Training saved = trainingRepository.save(training);

        notificationService.sendToUser(saved.getAssignedUserId(), NotificationEvent.builder()
                .id(UUID.randomUUID().toString())
                .type("TRAINING_ASSIGNED")
                .severity("info")
                .title("Nueva capacitación asignada")
                .body(saved.getTitle() + " · " + saved.getShift())
                .taskId(saved.getId())
                .storeId(saved.getStoreId())
                .timestamp(Instant.now())
                .build());

        return toResponse(saved);
    }

    // ── Consultas ────────────────────────────────────────────────────────

    @Override
    public List<TrainingResponse> getMyTrainings(String userId) {
        return trainingRepository.findByAssignedUserIdAndActivoTrue(userId)
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

        emitProgressNotification(saved, next);

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

    // ── Notificaciones ───────────────────────────────────────────────────

    private void emitProgressNotification(Training training, TrainingStatus newStatus) {
        String type     = "TRAINING_UPDATED";
        String severity = "info";
        String title    = "Capacitación Actualizada";

        switch (newStatus) {
            case EN_CURSO -> {
                type  = "TRAINING_STARTED";
                title = "Capacitación Iniciada";
            }
            case COMPLETADA -> {
                type     = "TRAINING_COMPLETED";
                title    = "Capacitación Completada";
            }
            case NO_COMPLETADA -> {
                type     = "TRAINING_FAILED";
                severity = "warning";
                title    = "Capacitación No Completada";
            }
            default -> { }
        }

        notificationService.sendToStoreManagers(training.getStoreId(), NotificationEvent.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .severity(severity)
                .title(title)
                .body(training.getTitle() + " · " + training.getPosition())
                .taskId(training.getId())
                .storeId(training.getStoreId())
                .timestamp(Instant.now())
                .build());
    }

    // ── Mapper Training → TrainingResponse ──────────────────────────────

    private TrainingResponse toResponse(Training t) {
        TrainingProgress p = t.getProgress();
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
