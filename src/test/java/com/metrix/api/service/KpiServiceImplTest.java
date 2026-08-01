package com.metrix.api.service;

import com.metrix.api.dto.KpiSummaryResponse;
import com.metrix.api.dto.ShiftBreakdownResponse;
import com.metrix.api.model.Execution;
import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.repository.ExamRepository;
import com.metrix.api.repository.ExamSubmissionRepository;
import com.metrix.api.repository.IncidentRepository;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.repository.TrainingRepository;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre los dos fixes de KPI del 2026-08-01 (reporte del cliente):
 * Delegación Efectiva con denominador incorrecto y turnos sin tareas
 * ausentes del breakdown. Usa {@code getSummaryForTasks} (no toca repos).
 */
@ExtendWith(MockitoExtension.class)
class KpiServiceImplTest {

    @Mock private TaskRepository           taskRepository;
    @Mock private UserRepository           userRepository;
    @Mock private TrainingRepository       trainingRepository;
    @Mock private StoreRepository          storeRepository;
    @Mock private IncidentRepository       incidentRepository;
    @Mock private ExamRepository           examRepository;
    @Mock private ExamSubmissionRepository examSubmissionRepository;
    @Mock private RestTemplate             restTemplate;

    private KpiServiceImpl kpiService;

    @BeforeEach
    void setUp() {
        kpiService = new KpiServiceImpl(taskRepository, userRepository, trainingRepository,
                storeRepository, incidentRepository, examRepository, examSubmissionRepository,
                restTemplate);
    }

    private Task task(TaskStatus status, int reworkCount, String shift) {
        return Task.builder()
                .shift(shift)
                .reworkCount(reworkCount)
                .execution(Execution.builder().status(status).build())
                .build();
    }

    private Task criticalTask(TaskStatus status) {
        return Task.builder()
                .shift("MATUTINO")
                .critical(true)
                .execution(Execution.builder().status(status).build())
                .build();
    }

    // ── Delegación Efectiva ──────────────────────────────────────────────

    @Test
    void delegacionEfectiva_noDebeSer100PorcientoConTareasSinHacer() {
        // 1 completada sin rework + 3 nunca hechas (pending/in_progress/failed)
        List<Task> tasks = List.of(
                task(TaskStatus.COMPLETED, 0, "MATUTINO"),
                task(TaskStatus.PENDING, 0, "MATUTINO"),
                task(TaskStatus.IN_PROGRESS, 0, "MATUTINO"),
                task(TaskStatus.FAILED, 0, "MATUTINO"));

        KpiSummaryResponse summary = kpiService.getSummaryForTasks(tasks, "GLOBAL", "all");

        // Antes del fix: 1/1 completadas sin rework = 100%. Ahora: 1/4 = 25%.
        assertEquals(25.0, summary.getDelegacionEfectiva(), 0.01,
                "Delegación debe contar TODAS las tareas delegadas, no solo las completadas");
    }

    @Test
    void delegacionEfectiva_100PorcientoSoloSiTodoSeCompletoSinRework() {
        List<Task> tasks = List.of(
                task(TaskStatus.COMPLETED, 0, "MATUTINO"),
                task(TaskStatus.COMPLETED, 0, "VESPERTINO"));

        KpiSummaryResponse summary = kpiService.getSummaryForTasks(tasks, "GLOBAL", "all");

        assertEquals(100.0, summary.getDelegacionEfectiva(), 0.01);
    }

    @Test
    void delegacionEfectiva_completadaConReworkNoCuentaComoEfectiva() {
        List<Task> tasks = List.of(
                task(TaskStatus.COMPLETED, 0, "MATUTINO"),
                task(TaskStatus.COMPLETED, 2, "MATUTINO"));

        KpiSummaryResponse summary = kpiService.getSummaryForTasks(tasks, "GLOBAL", "all");

        assertEquals(50.0, summary.getDelegacionEfectiva(), 0.01);
    }

    // ── Críticas pendientes ───────────────────────────────────────────────

    @Test
    void criticasPendientes_cuentaLasEnCurso() {
        // El rótulo de la UI dice "críticas que aún no se han completado".
        // Una crítica IN_PROGRESS tampoco está completada y debe contar.
        List<Task> tasks = List.of(
                criticalTask(TaskStatus.PENDING),
                criticalTask(TaskStatus.IN_PROGRESS),
                criticalTask(TaskStatus.FAILED),
                criticalTask(TaskStatus.COMPLETED));

        KpiSummaryResponse summary = kpiService.getSummaryForTasks(tasks, "GLOBAL", "all");

        assertEquals(3, summary.getCriticalPending(),
                "PENDING + IN_PROGRESS + FAILED; solo COMPLETED queda fuera");
    }

    @Test
    void criticasPendientes_ignoraLasNoCriticas() {
        List<Task> tasks = List.of(
                criticalTask(TaskStatus.PENDING),
                task(TaskStatus.PENDING, 0, "MATUTINO"),
                task(TaskStatus.IN_PROGRESS, 0, "MATUTINO"));

        KpiSummaryResponse summary = kpiService.getSummaryForTasks(tasks, "GLOBAL", "all");

        assertEquals(1, summary.getCriticalPending());
    }

    // ── Shift breakdown ───────────────────────────────────────────────────

    @Test
    void shiftBreakdown_incluyeSiempreLosTresTurnosCanonicos() {
        // Solo hay tareas en MATUTINO — VESPERTINO y NOCTURNO no deben desaparecer.
        List<Task> tasks = List.of(task(TaskStatus.COMPLETED, 0, "MATUTINO"));

        KpiSummaryResponse summary = kpiService.getSummaryForTasks(tasks, "GLOBAL", "all");

        Map<String, ShiftBreakdownResponse> byShift = summary.getShiftBreakdown().stream()
                .collect(Collectors.toMap(ShiftBreakdownResponse::getShift, s -> s));

        assertEquals(3, summary.getShiftBreakdown().size());
        assertTrue(byShift.containsKey("MATUTINO"));
        assertTrue(byShift.containsKey("VESPERTINO"));
        assertTrue(byShift.containsKey("NOCTURNO"));
    }

    @Test
    void shiftBreakdown_turnoSinTareasQuedaMarcadoComoVacio_noComoCero() {
        List<Task> tasks = List.of(task(TaskStatus.COMPLETED, 0, "MATUTINO"));

        KpiSummaryResponse summary = kpiService.getSummaryForTasks(tasks, "GLOBAL", "all");

        ShiftBreakdownResponse vespertino = summary.getShiftBreakdown().stream()
                .filter(s -> "VESPERTINO".equals(s.getShift()))
                .findFirst().orElseThrow();

        assertEquals(0, vespertino.getTotalTasks(), "turno sin tareas asignadas");
        assertEquals(-1.0, vespertino.getOnTimeRate(), 0.01, "centinela de sin-datos, no 0%");
    }

    @Test
    void shiftBreakdown_turnoConTareasSinCerrarNoEsIgualAVacio() {
        // VESPERTINO tiene una tarea, pero sigue PENDING (no cerrada aún).
        List<Task> tasks = List.of(
                task(TaskStatus.COMPLETED, 0, "MATUTINO"),
                task(TaskStatus.PENDING, 0, "VESPERTINO"));

        KpiSummaryResponse summary = kpiService.getSummaryForTasks(tasks, "GLOBAL", "all");

        ShiftBreakdownResponse vespertino = summary.getShiftBreakdown().stream()
                .filter(s -> "VESPERTINO".equals(s.getShift()))
                .findFirst().orElseThrow();

        assertEquals(1, vespertino.getTotalTasks(), "sí tiene una tarea asignada");
        assertEquals(0, vespertino.getTotalClosed(), "pero ninguna cerrada todavía");
        assertEquals(-1.0, vespertino.getOnTimeRate(), 0.01, "sin datos de on-time, no 0%");
    }
}
