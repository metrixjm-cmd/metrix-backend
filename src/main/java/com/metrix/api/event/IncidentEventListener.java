package com.metrix.api.event;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.event.DomainEvents.IncidentCreatedEvent;
import com.metrix.api.event.DomainEvents.IncidentStatusChangedEvent;
import com.metrix.api.model.IncidentStatus;
import com.metrix.api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Listens for incident domain events and dispatches SSE notifications.
 * Decouples IncidentServiceImpl from NotificationService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentEventListener {

    private final NotificationService notificationService;

    @EventListener
    public void onIncidentCreated(IncidentCreatedEvent event) {
        String severity = "CRITICA".equals(event.severity()) ? "critical" : "warning";
        String title = "CRITICA".equals(event.severity())
                ? "Incidencia CRITICA reportada"
                : "Nueva incidencia reportada";

        notificationService.sendToStoreManagers(event.storeId(), NotificationEvent.builder()
                .id(UUID.randomUUID().toString())
                .type("INCIDENT_CREATED")
                .severity(severity)
                .title(title)
                .body(event.title() + " · " + event.reporterName() + " · " + event.shift())
                .storeId(event.storeId())
                .timestamp(Instant.now())
                .build());
    }

    @EventListener
    public void onIncidentStatusChanged(IncidentStatusChangedEvent event) {
        switch (event.toStatus()) {
            case EN_RESOLUCION ->
                notificationService.sendToUser(event.reporterUserId(), NotificationEvent.builder()
                        .id(UUID.randomUUID().toString())
                        .type("INCIDENT_IN_RESOLUTION")
                        .severity("info")
                        .title("Incidencia en resolucion")
                        .body(event.title() + " ha sido tomada por el equipo.")
                        .storeId(event.storeId())
                        .timestamp(Instant.now())
                        .build());

            case CERRADA -> {
                String notes = event.resolutionNotes();
                notificationService.sendToUser(event.reporterUserId(), NotificationEvent.builder()
                        .id(UUID.randomUUID().toString())
                        .type("INCIDENT_RESOLVED")
                        .severity("info")
                        .title("Incidencia cerrada")
                        .body(event.title() + " ha sido resuelta: " +
                              (notes != null ? notes.substring(0, Math.min(60, notes.length())) : ""))
                        .storeId(event.storeId())
                        .timestamp(Instant.now())
                        .build());
            }

            case ABIERTA ->
                notificationService.sendToStoreManagers(event.storeId(), NotificationEvent.builder()
                        .id(UUID.randomUUID().toString())
                        .type("INCIDENT_REOPENED")
                        .severity("warning")
                        .title("Incidencia reabierta")
                        .body(event.title() + " ha sido reabierta para nueva revision.")
                        .storeId(event.storeId())
                        .timestamp(Instant.now())
                        .build());
        }
    }
}
