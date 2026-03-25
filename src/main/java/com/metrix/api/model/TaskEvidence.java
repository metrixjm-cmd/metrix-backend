package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Evidence file associated with a task, stored in a separate collection.
 * <p>
 * Separating evidence from the Task document prevents unbounded document growth
 * (a task with 50+ photos would bloat the Task document). Each evidence is its
 * own lightweight document with a reference back to the task.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "task_evidences")
public class TaskEvidence {

    @Id
    private String id;

    @Indexed
    @Field("task_id")
    private String taskId;

    @Field("store_id")
    private String storeId;

    /** IMAGE or VIDEO */
    @Field("type")
    private String type;

    @Field("url")
    private String url;

    @Field("uploaded_by")
    private String uploadedBy;

    @CreatedDate
    @Field("uploaded_at")
    private Instant uploadedAt;
}
