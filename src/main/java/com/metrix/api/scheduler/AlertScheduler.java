package com.metrix.api.scheduler;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.service.KpiService;
import com.metrix.api.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alertas preventivas programadas (Sprint 16).
 * <p>
 * Deduplicacion via ConcurrentHashMap local (sin Redis).
 * Con Cloud Run max-instances=1, no hay riesgo de alertas duplicadas.
 * Los sets se limpian cada hora automaticamente.
 */
@Slf4j
@Component
public class AlertScheduler {

    private final TaskRepository taskRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final KpiService kpiService;
    private final NotificationService notificationService;

    private final Set<String> warnedDeadlineIds = ConcurrentHashMap.newKeySet();
    private final Set<String> warnedOverdueIds = ConcurrentHashMap.newKeySet();

    private static final List<TaskStatus> OPEN_STATUSES =
            List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS);

    public AlertScheduler(
            TaskRepository taskRepository,
            StoreRepository storeRepository,
            UserRepository userRepository,
            KpiService kpiService,
            NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.kpiService = kpiService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void checkUpcomingDeadlines() {
        Instant now = Instant.now();
        Instant in30 = now.plusSeconds(30 * 60);

        List<com.metrix.api.model.Task> upcoming =
                taskRepository.findByExecution_StatusInAndDueAtBetweenAndActivoTrue(
                        OPEN_STATUSES, now, in30);

        for (com.metrix.api.model.Task task : upcoming) {
            if (!warnedDeadlineIds.add(task.getId())) {
                continue;
            }

            String assigneeName = resolveAssignedUserName(task.getAssignedUserId());
            NotificationEvent event = NotificationEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .type("TASK_DEADLINE_WARNING")
                    .severity("warning")
                    .title("Tarea proxima a vencer")
                    .body(String.format("%s · \"%s\" vence en menos de 30 minutos.", assigneeName, task.getTitle()))
                    .taskId(task.getId())
                    .incidentId(null)
                    .storeId(task.getStoreId())
                    .timestamp(now)
                    .build();

            if (task.getAssignedUserId() != null) {
                notificationService.sendToUser(task.getAssignedUserId(), event);
            }
            notificationService.sendToStoreManagers(task.getStoreId(), event);
            log.debug("TASK_DEADLINE_WARNING - taskId: {}", task.getId());
        }

        if (!upcoming.isEmpty()) {
            log.info("[AlertScheduler] checkUpcomingDeadlines - {} tareas proximas", upcoming.size());
        }
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void checkOverdueTasks() {
        Instant now = Instant.now();

        List<com.metrix.api.model.Task> overdue =
                taskRepository.findByExecution_StatusInAndDueAtBeforeAndActivoTrue(
                        OPEN_STATUSES, now);

        for (com.metrix.api.model.Task task : overdue) {
            if (!warnedOverdueIds.add(task.getId())) {
                continue;
            }

            String assigneeName = resolveAssignedUserName(task.getAssignedUserId());
            String severity = task.isCritical() ? "critical" : "warning";
            String title = task.isCritical() ? "Tarea critica vencida" : "Tarea vencida";

            NotificationEvent event = NotificationEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .type("TASK_OVERDUE")
                    .severity(severity)
                    .title(title)
                    .body(String.format("%s · \"%s\" supero su tiempo limite.", assigneeName, task.getTitle()))
                    .taskId(task.getId())
                    .incidentId(null)
                    .storeId(task.getStoreId())
                    .timestamp(now)
                    .build();

            if (task.getAssignedUserId() != null) {
                notificationService.sendToUser(task.getAssignedUserId(), event);
            }
            notificationService.sendToStoreManagers(task.getStoreId(), event);
            log.debug("TASK_OVERDUE - taskId: {} | critical: {}", task.getId(), task.isCritical());
        }

        if (!overdue.isEmpty()) {
            log.info("[AlertScheduler] checkOverdueTasks - {} tareas vencidas", overdue.size());
        }
    }

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
                            .title("Alerta de desempeno operativo")
                            .body(String.format("Sucursal \"%s\" inicia con IGEO %.1f%% (minimo: 70%%).",
                                    store.getNombre(), igeo))
                            .taskId(null)
                            .incidentId(null)
                            .storeId(store.getId())
                            .timestamp(now)
                            .build();

                    notificationService.sendToAllAdmins(event);
                    log.info("[AlertScheduler] DAILY_IGEO_ALERT - {} | IGEO: {}%",
                            store.getNombre(), igeo);
                }
            } catch (Exception e) {
                log.error("[AlertScheduler] Error IGEO para {}: {}", store.getId(), e.getMessage());
            }
        });
    }

    @Scheduled(cron = "0 0 * * * *")
    public void clearWarningSets() {
        int d = warnedDeadlineIds.size();
        int o = warnedOverdueIds.size();
        warnedDeadlineIds.clear();
        warnedOverdueIds.clear();
        if (d + o > 0) {
            log.info("[AlertScheduler] Sets limpiados - deadline: {} | overdue: {}", d, o);
        }
    }

    private String resolveAssignedUserName(String assignedUserId) {
        if (assignedUserId == null || assignedUserId.isBlank()) {
            return "Colaborador";
        }
        return userRepository.findById(assignedUserId)
                .map(user -> user.getNombre() != null && !user.getNombre().isBlank() ? user.getNombre() : "Colaborador")
                .orElse("Colaborador");
    }
}
