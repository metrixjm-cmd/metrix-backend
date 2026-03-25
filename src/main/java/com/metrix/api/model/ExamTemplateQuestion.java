package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Referencia a una pregunta del banco dentro de un ExamTemplate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamTemplateQuestion {

    /** MongoDB _id de la {@link BankQuestion} referenciada. */
    @Field("question_id")
    private String questionId;

    @Field("order")
    private int order;

    /** 0 = usar los puntos del banco. Positivo = sobreescribir. */
    @Builder.Default
    @Field("points_override")
    private int pointsOverride = 0;
}
