package com.metrix.api.scheduler;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.service.KpiService;
import com.metrix.api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Componente de alertas preventivas programadas (Sprint 16).
 * <p>
 * Cubre el Objetivo #8 de la spec: "Generación de avisos automáticos de tareas
 * terminadas o inconclusas". Complementa las notificaciones reactivas (Sprint 6)
 * con alertas proactivas que el sistema dispara sin intervención del usuario.
 * <p>
 * Alertas implementadas:
 * <ul>
 *   <li>{@code TASK_DEADLINE_WARNING} — tarea vence en menos de 30 minutos.</li>
 *   <li>{@code TASK_OVERDUE}          — tarea ya vencida y sigue abierta.</li>
 *   <li>{@code DAILY_IGEO_ALERT}      — sucursal con IGEO &lt; 70% al inicio del día.</li>
 * </ul>
 * <p>
 * Deduplicación: dos {@link Set} thread-safe evitan reenviar la misma alerta
 * dentro de la misma hora. Se limpian cada hora vía {@link #clearWarningSets()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScheduler {

    private final TaskRepository       taskRepository;
    private final StoreRepository      storeRepository;
    private final KpiService           kpiService;
    private final NotificationService  notificationService;

    /** IDs de tareas a las que ya se les envió TASK_DEADLINE_WARNING en la hora actual. */
    private final Set<String> warnedDeadlineIds = ConcurrentHashMap.newKeySet();

    /** IDs de tareas a las que ya se les envió TASK_OVERDUE en la hora actual. */
    private final Set<String> warnedOverdueIds  = ConcurrentHashMap.newKeySet();

    /** Statuses que se consideran "aún abiertas" para alertas. */
    private static final List<TaskStatus> OPEN_STATUSES =
            List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS);

    // ── Alertas de vencimiento próximo ────────────────────────────────────

    /**
     * Cada 5 minutos detecta tareas que vencen en los próximos 30 minutos
     * y aún no han sido cerradas.
     * <p>
     * Notifica al EJECUTADOR asignado y a los managers de la sucursal.
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void checkUpcomingDeadlines() {
        Instant now    = Instant.now();
        Instant in30   = now.plusSeconds(30 * 60);

        List<com.metrix.api.model.Task> upcoming =
                taskRepository.findByExecution_StatusInAndDueAtBetweenAndActivoTrue(
                        OPEN_STATUSES, now, in30);

        for (com.metrix.api.model.Task task : upcoming) {
            if (warnedDeadlineIds.contains(task.getId())) continue;

            NotificationEvent event = NotificationEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .type("TASK_DEADLINE_WARNING")
                    .severity("warning")
                    .title("Tarea próxima a vencer")
                    .body(String.format("«%s» vence en menos de 30 minutos.", task.getTitle()))
                    .taskId(task.getId())
                    .incidentId(null)
                    .storeId(task.getStoreId())
                    .timestamp(now)
                    .build();

            if (task.getAssignedUserId() != null) {
                notificationService.sendToUser(task.getAssignedUserId(), event);
            }
            notificationService.sendToStoreManagers(task.getStoreId(), event);

            warnedDeadlineIds.add(task.getId());
            log.debug("TASK_DEADLINE_WARNING enviado — taskId: {}", task.getId());
        }

        if (!upcoming.isEmpty()) {
            log.info("[AlertScheduler] checkUpcomingDeadlines — {} tareas próximas a vencer",
                    upcoming.size());
        }
    }

    // ── Alertas de tareas vencidas ────────────────────────────────────────

    /**
     * Cada 10 minutos detecta tareas cuyo {@code dueAt} ya pasó y siguen abiertas.
     * Las tareas críticas emiten severidad {@code critical}; el resto {@code warning}.
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void checkOverdueTasks() {
        Instant now = Instant.now();

        List<com.metrix.api.model.Task> overdue =
                taskRepository.findByExecution_StatusInAndDueAtBeforeAndActivoTrue(
                        OPEN_STATUSES, now);

        for (com.metrix.api.model.Task task : overdue) {
            if (warnedOverdueIds.contains(task.getId())) continue;

            String severity = task.isCritical() ? "critical" : "warning";
            String title    = task.isCritical() ? "Tarea crítica vencida" : "Tarea vencida";
            String body     = String.format("«%s» superó su tiempo límite sin cerrarse.", task.getTitle());

            NotificationEvent event = NotificationEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .type("TASK_OVERDUE")
                    .severity(severity)
                    .title(title)
                    .body(body)
                    .taskId(task.getId())
                    .incidentId(null)
                    .storeId(task.getStoreId())
                    .timestamp(now)
                    .build();

            if (task.getAssignedUserId() != null) {
                notificationService.sendToUser(task.getAssignedUserId(), event);
            }
            notificationService.sendToStoreManagers(task.getStoreId(), event);

            warnedOverdueIds.add(task.getId());
            log.debug("TASK_OVERDUE enviado — taskId: {} | critical: {}",
                    task.getId(), task.isCritical());
        }

        if (!overdue.isEmpty()) {
            log.info("[AlertScheduler] checkOverdueTasks — {} tareas vencidas detectadas",
                    overdue.size());
        }
    }

    // ── Resumen diario de IGEO ────────────────────────────────────────────

    /**
     * A las 08:00 AM revisa cada sucursal activa. Si su IGEO calculado es menor
     * a 70% (y existe al menos una tarea — IGEO ≥ 0), envía {@code DAILY_IGEO_ALERT}
     * a todos los ADMINs conectados.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyIgeoAlert() {
        Instant now = Instant.now();

        storeRepository.findByActivoTrue().forEach(store -> {
            try {
                double igeo = kpiService.getStoreSummary(store.getId()).getIgeo();
                if (igeo >= 0 && igeo < 70) {
                    NotificationEvent event = NotificationEvent.builder()
                            .id(UUID.randomUUID().toString())
                            .type("DAILY_IGEO_ALERT")
                            .severity("critical")
                            .title("Alerta de desempeño operativo")
                            .body(String.format("Sucursal «%s» inicia el día con IGEO %.1f%% (mínimo requerido: 70%%).",
                                    store.getNombre(), igeo))
                            .taskId(null)
                            .incidentId(null)
                            .storeId(store.getId())
                            .timestamp(now)
                            .build();

                    notificationService.sendToAllAdmins(event);
                    log.info("[AlertScheduler] DAILY_IGEO_ALERT — sucursal: {} | IGEO: {}%",
                            store.getNombre(), igeo);
                }
            } catch (Exception e) {
                log.warn("[AlertScheduler] Error calculando IGEO para sucursal {}: {}",
                        store.getId(), e.getMessage());
            }
        });
    }

    // ── Limpieza horaria de sets de deduplicación ─────────────────────────

    /**
     * Al inicio de cada hora limpia los sets de deduplicación para que las alertas
     * puedan volver a enviarse si la tarea sigue sin cerrarse.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void clearWarningSets() {
        int deadlineCleared = warnedDeadlineIds.size();
        int overdueCleared  = warnedOverdueIds.size();
        warnedDeadlineIds.clear();
        warnedOverdueIds.clear();
        log.info("[AlertScheduler] Sets limpiados — deadline: {} | overdue: {}",
                deadlineCleared, overdueCleared);
    }
}
