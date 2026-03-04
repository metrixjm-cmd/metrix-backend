package com.metrix.api.dto;

import com.metrix.api.model.IncidentCategory;
import com.metrix.api.model.IncidentSeverity;
import com.metrix.api.model.IncidentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class IncidentResponse {

    private String id;
    private String title;
    private String description;
    private IncidentCategory category;
    private IncidentSeverity severity;
    private String taskId;

    private String reporterUserId;
    private String reporterName;
    private String reporterPosition;
    private String storeId;
    private String shift;

    private IncidentStatus status;
    private String resolvedByUserId;
    private String resolutionNotes;
    private Instant resolvedAt;

    private List<String> evidenceUrls;

    private Instant createdAt;
    private Instant updatedAt;
}
