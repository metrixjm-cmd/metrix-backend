package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Sub-documento de ejecución de una tarea (Obj. #4: Control de Estatus).
 * <p>
 * Encapsula el ciclo de vida operativo completo:
 * estado → timestamps → resultado (onTime) → evidencias multimedia.
 * <p>
 * Decisiones de diseño:
 * - {@code onTime} se calcula automáticamente en {@code TaskServiceImpl}:
 *   {@code onTime = finishedAt.compareTo(dueAt) <= 0}. Nunca se setea manualmente.
 * - {@code evidence} se inicializa vacío para soporte de appends progresivos (Sprint 3).
 * - No lleva {@code @Id} ni {@code @Document}: es un embedded object dentro de Task.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Execution {

    /**
     * Estado actual en el flujo operativo.
     * Inicializado en PENDING al crear la tarea.
     */
    @Builder.Default
    @Field("status")
    private TaskStatus status = TaskStatus.PENDING;

    /**
     * Timestamp cuando el EJECUTADOR tomó la tarea (PENDING → IN_PROGRESS).
     * Alimenta el KPI #4: Tiempo Promedio de Ejecución.
     */
    @Field("started_at")
    private Instant startedAt;

    /**
     * Timestamp de cierre (COMPLETED o FAILED).
     * Con {@code startedAt} permite calcular duración real vs. estándar (KPI #4).
     */
    @Field("finished_at")
    private Instant finishedAt;

    /**
     * Indicador de cumplimiento en tiempo.
     * Null mientras la tarea no esté cerrada.
     * {@code true}  = finishedAt ≤ dueAt → alimenta KPI #1 (On-Time Rate).
     * {@code false} = finishedAt > dueAt o tarea FAILED.
     */
    @Field("on_time")
    private Boolean onTime;

    /**
     * Evidencias multimedia registradas durante la ejecución (Obj. #13).
     * Inicializado vacío; se puebla vía Sprint 3 (GCS upload).
     */
    @Builder.Default
    @Field("evidence")
    private Evidence evidence = new Evidence();
}
