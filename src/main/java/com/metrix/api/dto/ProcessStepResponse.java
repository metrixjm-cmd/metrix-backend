package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessStepResponse {

    private String stepId;
    private String title;
    private String description;
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    private boolean completed;
    private String notes;
    private int order;
    private Instant completedAt;
}
