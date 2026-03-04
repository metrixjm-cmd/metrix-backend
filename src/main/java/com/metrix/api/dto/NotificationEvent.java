package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Payload de un evento de notificación enviado vía SSE (Sprint 6).
 * <p>
 * Este DTO viaja serializado como JSON dentro del campo {@code data}
 * del protocolo Server-Sent Events al cliente Angular.
 * <p>
 * Tipos de evento ({@code type}):
 * <ul>
 *   <li>{@code TASK_ASSIGNED}   – nueva tarea asignada al EJECUTADOR.</li>
 *   <li>{@code TASK_STARTED}    – EJECUTADOR inició una tarea (notifica al GERENTE).</li>
 *   <li>{@code TASK_COMPLETED}  – tarea completada a tiempo (notifica al GERENTE).</li>
 *   <li>{@code TASK_FAILED}     – tarea fallida (notifica al GERENTE y ADMIN).</li>
 * </ul>
 * <p>
 * Severidad ({@code severity}):
 * <ul>
 *   <li>{@code critical} – FAILED, tareas críticas vencidas.</li>
 *   <li>{@code warning}  – próximas a vencer, retrabajos.</li>
 *   <li>{@code info}     – ASSIGNED, STARTED, COMPLETED.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    /** UUID único del evento. */
    private String id;

    /** Tipo de evento semántico. */
    private String type;

    /** Nivel de severidad visual: critical | warning | info */
    private String severity;

    /** Título corto mostrado en el panel de notificaciones. */
    private String title;

    /** Descripción del evento con contexto operativo. */
    private String body;

    /** MongoDB _id de la tarea relacionada (puede ser null). */
    private String taskId;

    /** MongoDB _id de la incidencia relacionada (puede ser null — Sprint 15). */
    private String incidentId;

    /** ID de la sucursal origen del evento. */
    private String storeId;

    /** Timestamp de generación del evento. */
    private Instant timestamp;
}
