package com.metrix.api.event;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.event.DomainEvents.TrainingCreatedEvent;
import com.metrix.api.event.DomainEvents.TrainingProgressChangedEvent;
import com.metrix.api.model.TrainingStatus;
import com.metrix.api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingEventListener {

    private final NotificationService notificationService;

    @EventListener
    public void onTrainingCreated(TrainingCreatedEvent event) {
        notificationService.sendToUser(event.assignedUserId(), NotificationEvent.builder()
                .id(UUID.randomUUID().toString())
                .type("TRAINING_ASSIGNED")
                .severity("info")
                .title("Nueva capacitacion asignada")
                .body(event.title() + " · " + event.shift())
                .taskId(event.trainingId())
                .storeId(event.storeId())
                .timestamp(Instant.now())
                .build());
    }

    @EventListener
    public void onTrainingProgressChanged(TrainingProgressChangedEvent event) {
        String type = switch (event.newStatus()) {
            case EN_CURSO       -> "TRAINING_STARTED";
            case COMPLETADA     -> "TRAINING_COMPLETED";
            case NO_COMPLETADA  -> "TRAINING_FAILED";
            case PROGRAMADA     -> "TRAINING_UPDATED";
        };
        String severity = event.newStatus() == TrainingStatus.NO_COMPLETADA ? "warning" : "info";
        String title = switch (event.newStatus()) {
            case EN_CURSO       -> "Capacitacion Iniciada";
            case COMPLETADA     -> "Capacitacion Completada";
            case NO_COMPLETADA  -> "Capacitacion No Completada";
            case PROGRAMADA     -> "Capacitacion Actualizada";
        };

        notificationService.sendToStoreManagers(event.storeId(), NotificationEvent.builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .severity(severity)
                .title(title)
                .body(event.title() + " · " + event.position())
                .taskId(event.trainingId())
                .storeId(event.storeId())
                .timestamp(Instant.now())
                .build());
    }
}
