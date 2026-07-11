package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Notificación persistida devuelta por GET /api/v1/notifications. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private String id;
    private String type;
    private String severity;
    private String title;
    private String body;
    private String taskId;
    private String incidentId;
    private String examId;
    private String storeId;
    private boolean read;
    private Instant timestamp;
}
