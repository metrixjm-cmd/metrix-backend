package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Día ya cerrado de una tarea repetitiva. El día de hoy vive en {@code execution}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringDay {

    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String MISSED = "MISSED";

    /** Fecha operativa yyyy-MM-dd. */
    @Field("date")
    private String date;

    @Field("status")
    private String status;

    @Field("on_time")
    private Boolean onTime;
}
