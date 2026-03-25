package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Sub-documento embebido en {@link Training}.
 * Referencia a un material del banco + tracking de si el EJECUTADOR ya lo vio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingMaterialRef {

    /** MongoDB _id del {@link TrainingMaterial} referenciado. */
    @Field("material_id")
    private String materialId;

    @Field("order")
    private int order;

    @Builder.Default
    @Field("required")
    private boolean required = true;

    @Field("notes")
    private String notes;

    // ── Tracking individual del ejecutador ───────────────────────────────

    /** El ejecutador marcó este material como visto. */
    @Builder.Default
    @Field("viewed")
    private boolean viewed = false;

    @Field("viewed_at")
    private Instant viewedAt;
}
