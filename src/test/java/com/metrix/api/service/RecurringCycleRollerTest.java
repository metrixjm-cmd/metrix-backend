package com.metrix.api.service;

import com.metrix.api.model.Execution;
import com.metrix.api.model.ProcessStep;
import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecurringCycleRollerTest {

    private final RecurringCycleRoller roller = new RecurringCycleRoller("America/Mexico_City");

    /** Martes 24 nov 2026, mediodía en Ciudad de México. */
    private static final Instant NOW = Instant.parse("2026-11-24T18:00:00Z");

    @Test
    void diaAnteriorSinHacerQuedaRegistradoYHoyEmpiezaLimpio() {
        Task task = recurring("2026-11-23", TaskStatus.PENDING, true);

        assertTrue(roller.roll(task, NOW));

        assertEquals("2026-11-24", task.getCycleDate());
        assertEquals(TaskStatus.PENDING, task.getExecution().getStatus());
        assertFalse(task.getProcesses().get(0).isCompleted());
        assertEquals(1, task.getDayHistory().size());
        assertEquals("2026-11-23", task.getDayHistory().get(0).getDate());
        assertEquals("MISSED", task.getDayHistory().get(0).getStatus());
    }

    @Test
    void diaAnteriorCompletadoTambienSeArchiva() {
        Task task = recurring("2026-11-23", TaskStatus.COMPLETED, true);
        task.getExecution().setOnTime(true);

        assertTrue(roller.roll(task, NOW));

        assertEquals("COMPLETED", task.getDayHistory().get(0).getStatus());
        assertEquals(Boolean.TRUE, task.getDayHistory().get(0).getOnTime());
        assertEquals(TaskStatus.PENDING, task.getExecution().getStatus());
    }

    @Test
    void elDiaDeHoyNoSeReinicia() {
        Task task = recurring("2026-11-24", TaskStatus.IN_PROGRESS, true);

        assertFalse(roller.roll(task, NOW));
        assertTrue(task.getProcesses().get(0).isCompleted());
        assertEquals(TaskStatus.IN_PROGRESS, task.getExecution().getStatus());
    }

    @Test
    void unDiaQueNoTocaNoSeArchiva() {
        Task task = recurring("2026-11-23", TaskStatus.PENDING, true);
        task.setRecurrenceDays(new ArrayList<>(List.of("VIE")));

        assertTrue(roller.roll(task, NOW));
        assertTrue(task.getDayHistory() == null || task.getDayHistory().isEmpty());
        assertEquals(LocalDate.parse("2026-11-24").toString(), task.getCycleDate());
    }

    private static Task recurring(String cycleDate, TaskStatus status, boolean stepDone) {
        return Task.builder()
                .recurring(true)
                .recurrenceDays(new ArrayList<>(List.of("LUN", "MAR", "MIE", "JUE", "VIE", "SAB")))
                .cycleDate(cycleDate)
                .execution(Execution.builder().status(status).build())
                .processes(new ArrayList<>(List.of(ProcessStep.builder()
                        .stepId("p1")
                        .title("Recepción")
                        .completed(stepDone)
                        .build())))
                .build();
    }
}
