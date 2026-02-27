package com.metrix.api.model;

/**
 * Estados del ciclo de vida de una tarea en METRIX.
 * <p>
 * Flujo válido (Obj. #4: Control de Estatus):
 * <pre>
 *   PENDING → IN_PROGRESS → COMPLETED
 *                        ↘ FAILED
 * </pre>
 * PENDING     → Tarea creada y asignada, sin iniciar.
 * IN_PROGRESS → El EJECUTADOR tomó la tarea (startedAt registrado).
 * COMPLETED   → Finalizada dentro del plazo (onTime=true).
 * FAILED      → No ejecutada, rechazada o vencida. Alimenta KPI #8 y KPI #3.
 */
public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
