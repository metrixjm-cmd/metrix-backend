package com.metrix.api.service;

import com.metrix.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para TrainingStateMachine.
 * Cubren transiciones válidas/inválidas, auto-complete por materiales,
 * y cálculo de porcentaje sin side-effects.
 */
class TrainingStateMachineTest {

    private TrainingStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new TrainingStateMachine();
    }

    // ── Transiciones válidas ────────────────────────────────────────────

    @Test
    void programada_to_enCurso_setsStartedAt() {
        Training t = buildTraining(TrainingStatus.PROGRAMADA);

        stateMachine.transitionByCommand(t, TrainingStatus.EN_CURSO, null, null, null);

        assertEquals(TrainingStatus.EN_CURSO, t.getProgress().getStatus());
        assertNotNull(t.getProgress().getStartedAt());
    }

    @Test
    void enCurso_to_completada_requiresGrade() {
        Training t = buildTraining(TrainingStatus.EN_CURSO);

        stateMachine.transitionByCommand(t, TrainingStatus.COMPLETADA, 8.5, null, null);

        assertEquals(TrainingStatus.COMPLETADA, t.getProgress().getStatus());
        assertEquals(8.5, t.getProgress().getGrade());
        assertTrue(t.getProgress().getPassed()); // 8.5 >= 7.0 default
        assertNotNull(t.getProgress().getCompletedAt());
        assertEquals(100, t.getProgress().getPercentage());
    }

    @Test
    void enCurso_to_completada_withoutGrade_throws() {
        Training t = buildTraining(TrainingStatus.EN_CURSO);

        assertThrows(IllegalStateException.class,
                () -> stateMachine.transitionByCommand(t, TrainingStatus.COMPLETADA, null, null, null));
    }

    @Test
    void enCurso_to_noCompletada_requiresComments() {
        Training t = buildTraining(TrainingStatus.EN_CURSO);

        stateMachine.transitionByCommand(t, TrainingStatus.NO_COMPLETADA, null, "No asistió", null);

        assertEquals(TrainingStatus.NO_COMPLETADA, t.getProgress().getStatus());
        assertEquals("No asistió", t.getProgress().getComments());
        assertFalse(t.getProgress().getOnTime());
    }

    @Test
    void enCurso_to_noCompletada_withoutComments_throws() {
        Training t = buildTraining(TrainingStatus.EN_CURSO);

        assertThrows(IllegalStateException.class,
                () -> stateMachine.transitionByCommand(t, TrainingStatus.NO_COMPLETADA, null, null, null));
    }

    // ── Transiciones inválidas ──────────────────────────────────────────

    @Test
    void programada_to_completada_directlyIsInvalid() {
        Training t = buildTraining(TrainingStatus.PROGRAMADA);

        assertThrows(IllegalStateException.class,
                () -> stateMachine.transitionByCommand(t, TrainingStatus.COMPLETADA, 10.0, null, null));
    }

    @Test
    void completada_isTerminal() {
        Training t = buildTraining(TrainingStatus.COMPLETADA);

        assertThrows(IllegalStateException.class,
                () -> stateMachine.transitionByCommand(t, TrainingStatus.EN_CURSO, null, null, null));
    }

    @Test
    void noCompletada_isTerminal() {
        Training t = buildTraining(TrainingStatus.NO_COMPLETADA);

        assertThrows(IllegalStateException.class,
                () -> stateMachine.transitionByCommand(t, TrainingStatus.EN_CURSO, null, null, null));
    }

    // ── Auto-complete por materiales ────────────────────────────────────

    @Test
    void firstMaterialViewed_autoTransitionsToProgramadaToEnCurso() {
        Training t = buildTrainingWithMaterials(TrainingStatus.PROGRAMADA, 3, 0);
        t.getMaterials().get(0).setViewed(true);
        t.getMaterials().get(0).setViewedAt(Instant.now());

        boolean changed = stateMachine.tryAutoCompleteByMaterials(t);

        assertTrue(changed);
        assertEquals(TrainingStatus.EN_CURSO, t.getProgress().getStatus());
        assertNotNull(t.getProgress().getStartedAt());
        assertEquals(33, t.getProgress().getPercentage()); // 1/3
    }

    @Test
    void allMaterialsViewed_autoCompletesWithGrade() {
        Training t = buildTrainingWithMaterials(TrainingStatus.EN_CURSO, 2, 2);

        boolean changed = stateMachine.tryAutoCompleteByMaterials(t);

        assertTrue(changed);
        assertEquals(TrainingStatus.COMPLETADA, t.getProgress().getStatus());
        assertNotNull(t.getProgress().getGrade());
        assertEquals(10.0, t.getProgress().getGrade());
        assertTrue(t.getProgress().getPassed());
        assertEquals(100, t.getProgress().getPercentage());
    }

    @Test
    void partialMaterialsViewed_doesNotComplete() {
        Training t = buildTrainingWithMaterials(TrainingStatus.EN_CURSO, 3, 1);

        boolean changed = stateMachine.tryAutoCompleteByMaterials(t);

        assertFalse(changed); // status stays EN_CURSO
        assertEquals(TrainingStatus.EN_CURSO, t.getProgress().getStatus());
        assertEquals(33, t.getProgress().getPercentage());
    }

    // ── computePercentage sin side-effects ──────────────────────────────

    @Test
    void computePercentage_doesNotMutateTraining() {
        Training t = buildTrainingWithMaterials(TrainingStatus.PROGRAMADA, 4, 2);
        TrainingStatus statusBefore = t.getProgress().getStatus();
        int percentageBefore = t.getProgress().getPercentage();

        int computed = stateMachine.computePercentage(t);

        assertEquals(50, computed);
        // Original no fue mutado
        assertEquals(statusBefore, t.getProgress().getStatus());
        assertEquals(percentageBefore, t.getProgress().getPercentage());
    }

    @Test
    void computePercentage_noMaterials_returnsStoredPercentage() {
        Training t = buildTraining(TrainingStatus.EN_CURSO);
        t.getProgress().setPercentage(42);
        t.setMaterials(new ArrayList<>());

        int computed = stateMachine.computePercentage(t);

        assertEquals(42, computed);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private Training buildTraining(TrainingStatus status) {
        return Training.builder()
                .id("t1")
                .minPassGrade(7.0)
                .dueAt(Instant.now().plusSeconds(86400))
                .materials(new ArrayList<>())
                .progress(TrainingProgress.builder().status(status).build())
                .build();
    }

    private Training buildTrainingWithMaterials(TrainingStatus status, int total, int viewed) {
        List<TrainingMaterialRef> materials = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            TrainingMaterialRef ref = TrainingMaterialRef.builder()
                    .materialId("mat-" + i)
                    .order(i + 1)
                    .required(true)
                    .viewed(i < viewed)
                    .viewedAt(i < viewed ? Instant.now() : null)
                    .build();
            materials.add(ref);
        }
        return Training.builder()
                .id("t1")
                .minPassGrade(7.0)
                .dueAt(Instant.now().plusSeconds(86400))
                .materials(materials)
                .progress(TrainingProgress.builder().status(status).build())
                .build();
    }
}
