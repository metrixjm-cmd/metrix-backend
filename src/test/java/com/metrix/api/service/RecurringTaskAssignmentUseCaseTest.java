package com.metrix.api.service;

import com.metrix.api.model.Execution;
import com.metrix.api.model.ProcessStep;
import com.metrix.api.model.Role;
import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dos casos de tarea repetitiva:
 * ADMIN asigna a GERENTE, y GERENTE asigna a un EJECUTADOR de su plantilla.
 * El día que no se cerró queda en el historial; el día de hoy empieza limpio.
 */
class RecurringTaskAssignmentUseCaseTest {

    private final RolePolicy rolePolicy = new RolePolicy();
    private final RecurringCycleRoller roller = new RecurringCycleRoller("America/Mexico_City");

    /** Martes 24 nov 2026, mediodía en Ciudad de México. */
    private static final Instant MARTES = Instant.parse("2026-11-24T18:00:00Z");

    @Test
    void adminEnviaRepetitivaAGerente_ayerSinHacerNoManchaHoy() {
        User admin = user("admin1", Role.ADMIN, "store-a");
        User gerente = user("ger1", Role.GERENTE, "store-a");
        User ejecutador = user("eje1", Role.EJECUTADOR, "store-a");

        assertDoesNotThrow(() -> rolePolicy.validateTaskAssignment(admin, gerente, "store-a"));
        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateTaskAssignment(admin, ejecutador, "store-a"));

        Task task = abiertaDesdeAyer(gerente.getId());
        task.getProcesses().get(0).setCompleted(true);

        assertTrue(roller.roll(task, MARTES));

        assertEquals(gerente.getId(), task.getAssignedUserId());
        assertHoyLimpio(task);
        assertEquals("2026-11-23", task.getDayHistory().get(0).getDate());
        assertEquals("MISSED", task.getDayHistory().get(0).getStatus());
    }

    @Test
    void gerenteEnviaRepetitivaAEjecutador_ayerFallidaNoManchaHoy() {
        User gerente = user("ger1", Role.GERENTE, "store-a");
        gerente.setNumeroUsuario("GER001");
        User ejecutador = user("eje1", Role.EJECUTADOR, "store-a");
        ejecutador.setManagerOwnerId(gerente.getId());
        User ajeno = user("eje2", Role.EJECUTADOR, "store-a");
        ajeno.setManagerOwnerId("ger2");

        assertDoesNotThrow(() -> rolePolicy.validateTaskAssignment(gerente, ejecutador, "store-a"));
        assertThrows(AccessDeniedException.class,
                () -> rolePolicy.validateTaskAssignment(gerente, ajeno, "store-a"));
        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateTaskAssignment(gerente, gerente, "store-a"));

        Task task = abiertaDesdeAyer(ejecutador.getId());
        task.getExecution().setStatus(TaskStatus.FAILED);
        task.getExecution().setOnTime(false);

        assertTrue(roller.roll(task, MARTES));

        assertEquals(ejecutador.getId(), task.getAssignedUserId());
        assertHoyLimpio(task);
        assertEquals("FAILED", task.getDayHistory().get(0).getStatus());
        assertEquals(Boolean.FALSE, task.getDayHistory().get(0).getOnTime());
    }

    private static void assertHoyLimpio(Task task) {
        LocalDate hoy = LocalDate.parse(task.getCycleDate());
        assertEquals(LocalDate.parse("2026-11-24"), hoy);
        assertEquals(DayOfWeek.TUESDAY, hoy.getDayOfWeek());
        assertEquals(TaskStatus.PENDING, task.getExecution().getStatus());
        assertFalse(task.getProcesses().get(0).isCompleted());
        assertEquals(1, task.getDayHistory().size());
    }

    private static Task abiertaDesdeAyer(String assigneeId) {
        return Task.builder()
                .assignedUserId(assigneeId)
                .storeId("store-a")
                .recurring(true)
                .recurrenceDays(new ArrayList<>(List.of("LUN", "MAR", "MIE", "JUE", "VIE", "SAB")))
                .cycleDate("2026-11-23")
                .execution(Execution.builder().status(TaskStatus.PENDING).build())
                .processes(new ArrayList<>(List.of(ProcessStep.builder()
                        .stepId("p1")
                        .title("Recepción del mostrador")
                        .completed(false)
                        .build())))
                .build();
    }

    private static User user(String id, Role role, String storeId) {
        return User.builder()
                .id(id)
                .nombre("Test " + id)
                .roles(Set.of(role))
                .storeId(storeId)
                .activo(true)
                .build();
    }
}
