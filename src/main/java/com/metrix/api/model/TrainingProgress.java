package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Sub-documento de progreso de una capacitación (patrón = Execution.java).
 * <p>
 * Encapsula el ciclo de vida operativo: estado → timestamps → resultado (onTime, grade, passed).
 * No lleva {@code @Id} ni {@code @Document}: es un embedded object dentro de Training.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingProgress {

    /** Estado actual del proceso de capacitación. Inicializado en PROGRAMADA. */
    @Builder.Default
    @Field("status")
    private TrainingStatus status = TrainingStatus.PROGRAMADA;

    /** Timestamp cuando el colaborador inició la capacitación (PROGRAMADA → EN_CURSO). */
    @Field("started_at")
    private Instant startedAt;

    /** Timestamp de cierre (COMPLETADA o NO_COMPLETADA). */
    @Field("completed_at")
    private Instant completedAt;

    /**
     * Indicador de cumplimiento en tiempo.
     * {@code true}  = completedAt ≤ dueAt
     * {@code false} = completedAt > dueAt o NO_COMPLETADA.
     */
    @Field("on_time")
    private Boolean onTime;

    /** Porcentaje de avance 0–100. Actualizable en EN_CURSO. */
    @Builder.Default
    @Field("percentage")
    private int percentage = 0;

    /** Calificación 0.0–10.0. Solo se asigna al completar. */
    @Field("grade")
    private Double grade;

    /** {@code true} si grade >= minPassGrade del Training padre. Null hasta completar. */
    @Field("passed")
    private Boolean passed;

    /** Comentarios del colaborador o causa de no completar. */
    @Field("comments")
    private String comments;
}
