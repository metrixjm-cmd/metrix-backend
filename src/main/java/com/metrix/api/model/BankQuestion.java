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
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pregunta reutilizable en el banco de preguntas de METRIX (E3).
 * <p>
 * Soporta dos tipos: TRUE_FALSE y MULTI_SELECT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "question_bank")
@CompoundIndexes({
        @CompoundIndex(name = "idx_qb_type_cat",  def = "{'type': 1, 'category': 1, 'activo': 1}"),
        @CompoundIndex(name = "idx_qb_diff_cat",  def = "{'difficulty': 1, 'category': 1, 'activo': 1}"),
        @CompoundIndex(name = "idx_qb_store",     def = "{'store_id': 1, 'activo': 1}")
})
public class BankQuestion {

    @Id
    private String id;

    @Version
    private Long version;

    @Field("question_text")
    private String questionText;

    @Indexed
    @Field("type")
    private QuestionType type;

    @Builder.Default
    @Field("options")
    private List<String> options = new ArrayList<>();

    /** Para TRUE_FALSE: índice de la opción correcta (0 = Verdadero, 1 = Falso). */
    @Field("correct_option_index")
    private int correctOptionIndex;

    /** Para MULTI_SELECT: índices de opciones correctas. */
    @Builder.Default
    @Field("correct_option_indexes")
    private List<Integer> correctOptionIndexes = new ArrayList<>();

    @Field("explanation")
    private String explanation;

    @Builder.Default
    @Field("points")
    private int points = 1;

    @Indexed
    @Field("category")
    private String category;

    @Field("difficulty")
    private QuestionDifficulty difficulty;

    @Builder.Default
    @Field("tags")
    private List<String> tags = new ArrayList<>();

    @Field("created_by")
    private String createdBy;

    @Field("creator_name")
    private String creatorName;

    @Indexed
    @Field("store_id")
    private String storeId;

    @Builder.Default
    @Field("usage_count")
    private int usageCount = 0;

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
