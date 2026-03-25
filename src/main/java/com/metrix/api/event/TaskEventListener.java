package com.metrix.api.event;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.event.DomainEvents.TaskCreatedEvent;
import com.metrix.api.event.DomainEvents.TaskStatusChangedEvent;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Listens for task domain events and dispatches SSE notifications.
 * <p>
 * This decouples TaskServiceImpl from NotificationService — the task module
 * only emits events, and this listener translates them into SSE notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskEventListener {

    private final NotificationService notificationService;

    @EventListener
    public void onTaskCreated(TaskCreatedEvent event) {
        notificationService.sendToUser(event.assignedUserId(), NotificationEvent.builder()
                .id(UUID.randomUUID().toString())
                .type("TASK_ASSIGNED")
                .severity("info")
                .title("Nueva tarea asignada")
                .body(event.title() + " · " + event.shift())
                .taskId(event.taskId())
                .storeId(event.storeId())
                .timestamp(Instant.now())
                .build());
    }

    @EventListener
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        String type = switch (event.toStatus()) {
            case IN_PROGRESS -> "TASK_STARTED";
            case COMPLETED   -> "TASK_COMPLETED";
            case FAILED      -> "TASK_FAILED";
            case PENDING     -> "TASK_REOPENED";
        };
        String severity = switch (event.toStatus()) {
            case IN_PROGRESS, COMPLETED -> "info";
            case FAILED                 -> "critical";
            case PENDING                -> "warning";
        };
        String title = switch (event.toStatus()) {
            case IN_PROGRESS -> "Tarea Iniciada";
            case COMPLETED   -> "Tarea Completada";
            case FAILED      -> "Tarea Fallida";
            case PENDING     -> "Tarea Reabierta";
        };

        String comments = event.comments();
        String body = event.title() + " · " + event.position()
                + (comments != null && !comments.isBlank()
                        ? " — " + comments.substring(0, Math.min(60, comments.length()))
                        : "");

        notificationService.sendToStoreManagers(event.storeId(), NotificationEvent.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .severity(severity)
                .title(title)
                .body(body)
                .taskId(event.taskId())
                .storeId(event.storeId())
                .timestamp(Instant.now())
                .build());
    }
}
