package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Examen evaluativo del módulo Trainer (Sprint 19).
 * <p>
 * Un examen pertenece a una sucursal y puede vincularse opcionalmente a una capacitación.
 * Los colaboradores lo responden y el sistema califica automáticamente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "exams")
@CompoundIndexes({
        @CompoundIndex(name = "idx_exam_store",    def = "{'store_id': 1, 'activo': 1}"),
        @CompoundIndex(name = "idx_exam_training", def = "{'training_id': 1, 'activo': 1}")
})
public class Exam {

    @Id
    private String id;

    @Version
    private Long version;

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    /** MongoDB _id de la capacitación relacionada. Opcional. */
    @Field("training_id")
    private String trainingId;

    @Field("store_id")
    private String storeId;

    @Builder.Default
    @Field("questions")
    private List<ExamQuestion> questions = new ArrayList<>();

    /** Porcentaje mínimo para aprobar (0–100). */
    @Builder.Default
    @Field("passing_score")
    private int passingScore = 70;

    /** Límite de tiempo en minutos. Null = sin límite. */
    @Field("time_limit_minutes")
    private Integer timeLimitMinutes;

    /** Número máximo de intentos permitidos. 0 = ilimitado. */
    @Builder.Default
    @Field("max_attempts")
    private int maxAttempts = 0;

    @Field("created_by_user_id")
    private String createdByUserId;

    @Field("created_by_name")
    private String createdByName;

    @Builder.Default
    @Field("activo")
    private boolean activo = true;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
