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
 * Plantilla reutilizable de examen (E4).
 * <p>
 * Agrupa referencias a preguntas del banco ({@link BankQuestion}) con configuración
 * predefinida. Al crear un {@link Exam} desde una plantilla, las preguntas se copian
 * como snapshot (inmutable) para preservar integridad histórica.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "exam_templates")
@CompoundIndexes({
        @CompoundIndex(name = "idx_etpl_cat",   def = "{'category': 1, 'activo': 1}"),
        @CompoundIndex(name = "idx_etpl_store", def = "{'store_id': 1, 'activo': 1}")
})
public class ExamTemplate {

    @Id
    private String id;

    @Version
    private Long version;

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Field("category")
    private String category;

    /** Porcentaje mínimo para aprobar (0–100). */
    @Builder.Default
    @Field("passing_score")
    private int passingScore = 70;

    /** Límite de tiempo en minutos. Null = sin límite. */
    @Field("time_limit_minutes")
    private Integer timeLimitMinutes;

    /** Si true, las preguntas se barajan al crear un Exam desde esta plantilla. */
    @Builder.Default
    @Field("shuffle_questions")
    private boolean shuffleQuestions = false;

    /** Si true, las opciones de cada pregunta se barajan. */
    @Builder.Default
    @Field("shuffle_options")
    private boolean shuffleOptions = false;

    /** 0 = intentos ilimitados. */
    @Builder.Default
    @Field("max_attempts")
    private int maxAttempts = 0;

    /** Referencias a preguntas del banco en orden definido. */
    @Builder.Default
    @Field("questions")
    private List<ExamTemplateQuestion> questions = new ArrayList<>();

    @Builder.Default
    @Field("tags")
    private List<String> tags = new ArrayList<>();

    @Field("created_by")
    private String createdBy;

    @Field("creator_name")
    private String creatorName;

    /** Null = plantilla global visible para todas las sucursales. */
    @Field("store_id")
    private String storeId;

    /** Cuántos exámenes se han creado usando esta plantilla. */
    @Builder.Default
    @Field("times_used")
    private int timesUsed = 0;

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
