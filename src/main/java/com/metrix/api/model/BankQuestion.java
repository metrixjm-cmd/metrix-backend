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
 * Las preguntas del banco son inmutables una vez usadas en un examen (usageCount > 0).
 * Editar una pregunta en uso crea inconsistencias históricas — preferir crear nueva versión.
 * <p>
 * Soporta los 4 tipos de QuestionType: MULTIPLE_CHOICE, MULTI_SELECT, TRUE_FALSE, OPEN_TEXT.
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

    // ── Contenido ──────────────────────────────────────────────────────────

    @Field("question_text")
    private String questionText;

    @Indexed
    @Field("type")
    private QuestionType type;

    /** Opciones de respuesta. Null / vacío para OPEN_TEXT. */
    @Builder.Default
    @Field("options")
    private List<String> options = new ArrayList<>();

    // ── Respuestas correctas ───────────────────────────────────────────────

    /** Para MULTIPLE_CHOICE y TRUE_FALSE: índice de la opción correcta. */
    @Field("correct_option_index")
    private int correctOptionIndex;

    /** Para MULTI_SELECT: índices de opciones correctas. */
    @Builder.Default
    @Field("correct_option_indexes")
    private List<Integer> correctOptionIndexes = new ArrayList<>();

    /** Para OPEN_TEXT: palabras clave aceptadas (matching case-insensitive). */
    @Builder.Default
    @Field("accepted_keywords")
    private List<String> acceptedKeywords = new ArrayList<>();

    // ── Metadata ──────────────────────────────────────────────────────────

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

    // ── Autoría ───────────────────────────────────────────────────────────

    @Field("created_by")
    private String createdBy;

    @Field("creator_name")
    private String creatorName;

    /**
     * Sucursal propietaria. Null = pregunta global visible para todos.
     */
    @Indexed
    @Field("store_id")
    private String storeId;

    // ── Métricas ──────────────────────────────────────────────────────────

    /** Cuántos exámenes han incluido esta pregunta (como snapshot). */
    @Builder.Default
    @Field("usage_count")
    private int usageCount = 0;

    // ── Meta ──────────────────────────────────────────────────────────────

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
