package com.metrix.api.event;

import com.metrix.api.model.IncidentStatus;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.model.TrainingStatus;

/**
 * Domain events for inter-module communication within the monolith.
 * <p>
 * These events decouple the task/incident modules from notifications and analytics,
 * allowing each module to evolve independently. Emitted via Spring's
 * {@link org.springframework.context.ApplicationEventPublisher} and consumed
 * via {@link org.springframework.context.event.EventListener}.
 */
public final class DomainEvents {

    private DomainEvents() {}

    // ── Task Events ───────────────────────────────────────────────────────

    public record TaskCreatedEvent(
            String taskId,
            String assignedUserId,
            String storeId,
            String title,
            String shift
    ) {}

    public record TaskStatusChangedEvent(
            String taskId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            String storeId,
            String assignedUserId,
            String assignedUserName,
            String title,
            String position,
            String comments
    ) {}

    // ── Incident Events ───────────────────────────────────────────────────

    public record IncidentCreatedEvent(
            String incidentId,
            String storeId,
            String reporterUserId,
            String title,
            String reporterName,
            String shift,
            String severity
    ) {}

    public record IncidentStatusChangedEvent(
            String incidentId,
            IncidentStatus fromStatus,
            IncidentStatus toStatus,
            String storeId,
            String reporterUserId,
            String title,
            String resolutionNotes
    ) {}

    // ── Training Events ─────────────────────────────────────────────────

    public record TrainingCreatedEvent(
            String trainingId,
            String assignedUserId,
            String storeId,
            String title,
            String shift
    ) {}

    public record TrainingProgressChangedEvent(
            String trainingId,
            TrainingStatus newStatus,
            String storeId,
            String title,
            String position
    ) {}
}
