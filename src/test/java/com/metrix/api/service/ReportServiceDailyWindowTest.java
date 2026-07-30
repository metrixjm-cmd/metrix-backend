package com.metrix.api.service;

import com.metrix.api.dto.DailyReportResponse;
import com.metrix.api.dto.KpiSummaryResponse;
import com.metrix.api.model.Execution;
import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Ventana del día en {@code buildDailyReport}.
 * <p>
 * La operación corre en UTC−6 y el servidor en UTC, así que el día natural del
 * reporte va de las 06:00Z a las 06:00Z del día siguiente. Estos tests fijan ese
 * corte y la regla de pertenencia (creada ese día <b>o</b> cerrada ese día).
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceDailyWindowTest {

    private static final String STORE = "store-1";
    private static final LocalDate DAY = LocalDate.of(2026, 7, 30);

    @Mock private TaskRepository       taskRepository;
    @Mock private UserRepository       userRepository;
    @Mock private StoreRepository      storeRepository;
    @Mock private KpiService           kpiService;
    @Mock private GamificationService  gamificationService;

    @InjectMocks private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "operationalZone", "America/Mexico_City");
    }

    @Test
    void incluyeLaTareaCreadaDespuesDeLas18HoraLocal() {
        // 2026-07-30 20:00 hora local = 2026-07-31 02:00Z.
        // Con la ventana en UTC caía en el reporte del 31.
        Task nocturna = task("t-nocturna", Instant.parse("2026-07-31T02:00:00Z"), TaskStatus.COMPLETED, null);
        when(taskRepository.findByStoreIdAndActivoTrue(STORE)).thenReturn(List.of(nocturna));

        DailyReportResponse report = build();

        assertEquals(1, report.getTotalAssigned());
        assertEquals("t-nocturna", report.getTasks().get(0).getId());
    }

    @Test
    void excluyeLaTareaDeLaNocheAnterior() {
        // 2026-07-29 20:00 local = 2026-07-30 02:00Z: pertenece al día 29, no al 30.
        Task vispera = task("t-vispera", Instant.parse("2026-07-30T02:00:00Z"), TaskStatus.COMPLETED, null);
        when(taskRepository.findByStoreIdAndActivoTrue(STORE)).thenReturn(List.of(vispera));

        assertEquals(0, build().getTotalAssigned());
    }

    @Test
    void incluyeLaTareaCreadaAntesPeroCerradaEseDia() {
        // Abierta el lunes, cerrada el día del reporte: es parte de ese cierre.
        Task arrastrada = task("t-arrastrada",
                Instant.parse("2026-07-27T15:00:00Z"),
                TaskStatus.COMPLETED,
                Instant.parse("2026-07-30T18:00:00Z"));
        when(taskRepository.findByStoreIdAndActivoTrue(STORE)).thenReturn(List.of(arrastrada));

        DailyReportResponse report = build();

        assertEquals(1, report.getTotalAssigned());
        assertEquals(1, report.getTotalCompleted());
    }

    @Test
    void excluyeLaTareaViejaQueSigueAbierta() {
        Task vieja = task("t-vieja", Instant.parse("2026-07-27T15:00:00Z"), TaskStatus.PENDING, null);
        when(taskRepository.findByStoreIdAndActivoTrue(STORE)).thenReturn(List.of(vieja));

        assertEquals(0, build().getTotalAssigned());
    }

    @Test
    void losKpisSeCalculanSobreLasTareasDelDiaNoSobreElHistorico() {
        Task delDia = task("t-dia", Instant.parse("2026-07-30T18:00:00Z"), TaskStatus.COMPLETED, null);
        Task vieja  = task("t-vieja", Instant.parse("2026-01-05T18:00:00Z"), TaskStatus.COMPLETED, null);
        when(taskRepository.findByStoreIdAndActivoTrue(STORE)).thenReturn(List.of(delDia, vieja));

        build();

        // El histórico de la sucursal ya no alimenta el bloque de KPIs del reporte.
        verify(kpiService, never()).getStoreSummary(anyString());
        verify(kpiService).getSummaryForTasks(
                argThat(tasks -> tasks.size() == 1 && "t-dia".equals(tasks.get(0).getId())),
                eq("STORE"), eq(STORE));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private DailyReportResponse build() {
        when(kpiService.getSummaryForTasks(any(), anyString(), anyString()))
                .thenReturn(KpiSummaryResponse.builder().build());
        return service.buildDailyReport(STORE, DAY);
    }

    private Task task(String id, Instant createdAt, TaskStatus status, Instant finishedAt) {
        Execution exec = new Execution();
        exec.setStatus(status);
        exec.setFinishedAt(finishedAt);

        Task task = new Task();
        task.setId(id);
        task.setTitle(id);
        task.setStoreId(STORE);
        task.setCreatedAt(createdAt);
        task.setExecution(exec);
        return task;
    }
}
