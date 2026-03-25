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
 * Plantilla reutilizable de capacitación en METRIX.
 * <p>
 * Agrupa materiales del banco ({@link TrainingMaterial}) en un orden definido
 * con metadatos de capacitación preconfigurados.
 * Al crear un {@link Training} desde plantilla, se copian todos los campos
 * y se referencian los mismos materiales (sin duplicar archivos).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "training_templates")
@CompoundIndexes({
        @CompoundIndex(name = "idx_category_active", def = "{'category': 1, 'activo': 1}"),
        @CompoundIndex(name = "idx_level_active",    def = "{'level': 1, 'activo': 1}")
})
public class TrainingTemplate {

    @Id
    private String id;

    @Version
    private Long version;

    // ── Definición ────────────────────────────────────────────────────────

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Indexed
    @Field("category")
    private String category;

    @Field("level")
    private TrainingLevel level;

    @Field("duration_hours")
    private int durationHours;

    @Field("min_pass_grade")
    private double minPassGrade;

    // ── Materiales ordenados ──────────────────────────────────────────────

    @Builder.Default
    @Field("materials")
    private List<TemplateMaterial> materials = new ArrayList<>();

    // ── Taxonomía ─────────────────────────────────────────────────────────

    @Builder.Default
    @Field("tags")
    private List<String> tags = new ArrayList<>();

    // ── Autoría ───────────────────────────────────────────────────────────

    @Field("created_by")
    private String createdBy;

    @Field("creator_name")
    private String creatorName;

    // ── Métricas ──────────────────────────────────────────────────────────

    /** Cuántas capacitaciones se han creado usando esta plantilla. */
    @Builder.Default
    @Field("times_used")
    private int timesUsed = 0;

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
