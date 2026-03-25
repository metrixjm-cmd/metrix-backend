package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of MongoDB aggregation that groups tasks by execution.status.
 * Used by KPI service to avoid loading all tasks into memory for simple counts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusCount {
    private String id;  // The status value (PENDING, IN_PROGRESS, COMPLETED, FAILED)
    private long count;
}
