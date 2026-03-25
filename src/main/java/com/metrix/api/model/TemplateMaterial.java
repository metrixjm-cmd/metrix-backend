package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Sub-documento embebido en {@link TrainingTemplate}.
 * Representa un material dentro de una plantilla, con su orden y metadatos de contexto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateMaterial {

    /** MongoDB _id del {@link TrainingMaterial} referenciado. */
    @Field("material_id")
    private String materialId;

    /** Posición dentro de la secuencia de la plantilla (1-based). */
    @Field("order")
    private int order;

    /** Si es false, el usuario puede saltárselo sin bloquear la completación. */
    @Builder.Default
    @Field("required")
    private boolean required = true;

    /** Instrucciones específicas del contexto de esta plantilla. */
    @Field("notes")
    private String notes;
}
