package com.metrix.api.service;

import com.metrix.api.model.Training;
import com.metrix.api.model.TrainingMaterialRef;
import com.metrix.api.model.TrainingProgress;
import com.metrix.api.model.TrainingStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados de capacitaciones — fuente única de transiciones.
 * <p>
 * Centraliza TODA la lógica de transiciones para evitar que existan
 * múltiples paths de reconciliación (bug: 100% pero EN_CURSO).
 * <p>
 * Transiciones válidas:
 * <ul>
 *   <li>PROGRAMADA  → EN_CURSO</li>
 *   <li>EN_CURSO    → EN_CURSO       (actualizar % sin cambiar estado)</li>
 *   <li>EN_CURSO    → COMPLETADA     (requiere grade)</li>
 *   <li>EN_CURSO    → NO_COMPLETADA  (requiere comments)</li>
 *   <li>COMPLETADA, NO_COMPLETADA    → terminales</li>
 * </ul>
 */
@Component
public class TrainingStateMachine {

    private static final Map<TrainingStatus, Set<TrainingStatus>> VALID_TRANSITIONS = Map.of(
            TrainingStatus.PROGRAMADA,     Set.of(TrainingStatus.EN_CURSO, TrainingStatus.NO_COMPLETADA),
            TrainingStatus.EN_CURSO,       Set.of(TrainingStatus.EN_CURSO, TrainingStatus.COMPLETADA,
                                                   TrainingStatus.NO_COMPLETADA),
            TrainingStatus.COMPLETADA,     Set.of(),
            TrainingStatus.NO_COMPLETADA,  Set.of()
    );

    // ── Transición explícita por comando (GERENTE completa / falla) ─────

    /**
     * Transición explícita invocada desde updateProgress().
     *
     * @param training  la capacitación a transicionar
     * @param newStatus estado destino
     * @param grade     calificación 0–10 (requerida para COMPLETADA)
     * @param comments  comentarios (requeridos para NO_COMPLETADA)
     * @param percentage porcentaje de avance opcional
     */
    public void transitionByCommand(Training training, TrainingStatus newStatus,
                                     Double grade, String comments, Integer percentage) {
        TrainingProgress progress = ensureProgress(training);
        validateTransition(progress.getStatus(), newStatus);

        Instant now = Instant.now();

        switch (newStatus) {
            case EN_CURSO -> {
                if (progress.getStatus() == TrainingStatus.PROGRAMADA) {
                    progress.setStartedAt(now);
                }
                progress.setStatus(TrainingStatus.EN_CURSO);
                if (percentage != null) {
                    progress.setPercentage(percentage);
                }
            }
            case COMPLETADA -> {
                if (grade == null) {
                    throw new IllegalStateException(
                            "Al marcar una capacitación como COMPLETADA debe proporcionar el campo 'grade' (0–10).");
                }
                progress.setStatus(TrainingStatus.COMPLETADA);
                progress.setCompletedAt(now);
                progress.setGrade(grade);
                progress.setPassed(grade >= training.getMinPassGrade());
                progress.setOnTime(training.getDueAt() == null || !now.isAfter(training.getDueAt()));
                if (percentage != null) {
                    progress.setPercentage(percentage);
                } else {
                    progress.setPercentage(100);
                }
            }
            case NO_COMPLETADA -> {
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
            default -> { /* PROGRAMADA no es destino válido */ }
        }
    }

    // ── Auto-transición por materiales (markMaterialViewed) ──────────────

    /**
     * Evalúa si marcar un material como visto debe disparar transición automática.
     * Solo se invoca desde markMaterialViewed(), NUNCA desde GETs.
     *
     * @return true si el estado cambió (para publicar evento)
     */
    public boolean tryAutoCompleteByMaterials(Training training) {
        List<TrainingMaterialRef> materials = training.getMaterials();
        if (materials == null || materials.isEmpty()) return false;

        TrainingProgress progress = ensureProgress(training);
        TrainingStatus statusBefore = progress.getStatus();

        long viewedCount = materials.stream().filter(TrainingMaterialRef::isViewed).count();
        int percentage = (int) Math.round((viewedCount * 100.0) / materials.size());
        progress.setPercentage(percentage);

        Instant now = Instant.now();

        // Auto-start: PROGRAMADA → EN_CURSO si primer material visto
        if (viewedCount > 0 && progress.getStatus() == TrainingStatus.PROGRAMADA) {
            progress.setStatus(TrainingStatus.EN_CURSO);
            progress.setStartedAt(now);
        }

        // Auto-complete: EN_CURSO → COMPLETADA si todos los materiales vistos
        if (viewedCount == materials.size() && progress.getStatus() == TrainingStatus.EN_CURSO) {
            progress.setStatus(TrainingStatus.COMPLETADA);
            progress.setCompletedAt(now);
            progress.setGrade(10.0);  // Nota máxima: completó todo el material
            progress.setPassed(10.0 >= training.getMinPassGrade());
            progress.setOnTime(training.getDueAt() == null || !now.isAfter(training.getDueAt()));
        }

        return progress.getStatus() != statusBefore;
    }

    // ── Cálculo de porcentaje sin side-effects (para toResponse en GETs) ─

    /**
     * Calcula el porcentaje de avance basado en materiales vistos.
     * NO modifica el Training — es puro cálculo para respuesta.
     */
    public int computePercentage(Training training) {
        List<TrainingMaterialRef> materials = training.getMaterials();
        if (materials == null || materials.isEmpty()) {
            TrainingProgress p = training.getProgress();
            return p != null ? p.getPercentage() : 0;
        }
        long viewed = materials.stream().filter(TrainingMaterialRef::isViewed).count();
        return (int) Math.round((viewed * 100.0) / materials.size());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void validateTransition(TrainingStatus current, TrainingStatus target) {
        if (!VALID_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalStateException(
                    String.format("Transición inválida: %s → %s. " +
                            "Flujo permitido: PROGRAMADA→EN_CURSO, EN_CURSO→COMPLETADA|NO_COMPLETADA.",
                            current, target));
        }
    }

    private TrainingProgress ensureProgress(Training training) {
        TrainingProgress progress = training.getProgress();
        if (progress == null) {
            progress = TrainingProgress.builder().build();
            training.setProgress(progress);
        }
        return progress;
    }
}
