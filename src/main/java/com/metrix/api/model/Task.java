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
 * Entidad principal de tarea en METRIX.
 * <p>
 * Implementa el modelo de datos unificado definido en METRIX_DEFINICION §4:
 * {@code task_definition} + {@code assignment} + {@code execution} + {@code audit}.
 * <p>
 * Objetivos cubiertos directamente:
 * <ul>
 *   <li>Obj. #1  – Delegación Inteligente: {@code assignedUserId}, {@code shift}, {@code storeId}.</li>
 *   <li>Obj. #4  – Control de Estatus: sub-documento {@link Execution} con flujo completo.</li>
 *   <li>Obj. #7  – Gestión del Tiempo: {@code dueAt} + cálculo automático de {@link Execution#onTime}.</li>
 *   <li>Obj. #11 – Almacenamiento de Resultados: {@code activo} soft-delete + auditoría temporal.</li>
 *   <li>Obj. #16 – Independencia MongoDB: @Document directamente mapeado al spec.</li>
 * </ul>
 * <p>
 * Índices compuestos optimizados para los casos de uso críticos:
 * <ul>
 *   <li>{@code idx_user_status}       → "mis tareas pendientes" (portfolio EJECUTADOR).</li>
 *   <li>{@code idx_store_shift_status} → vista gerencial por turno y sucursal.</li>
 *   <li>{@code idx_store_due}          → detección de tareas vencidas para alertas (Obj. #8).</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tasks")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_user_status",
                def = "{'assigned_user_id': 1, 'execution.status': 1}"),
        @CompoundIndex(
                name = "idx_store_shift_status",
                def = "{'store_id': 1, 'shift': 1, 'execution.status': 1}"),
        @CompoundIndex(
                name = "idx_store_due",
                def = "{'store_id': 1, 'due_at': 1}")
})
public class Task {

    @Id
    private String id;

    @Version
    private Long version;

    // ── Definición de la Tarea (task_definition) ─────────────────────────

    /** Título corto y descriptivo de la actividad (max 200 chars). */
    @Field("title")
    private String title;

    /** Descripción detallada: pasos, criterios de aceptación, recursos necesarios. */
    @Field("description")
    private String description;

    /** Categoría para dashboards especializados (Obj. #22). Ahora dinámica vía catálogo. */
    @Field("category")
    private String category;

    /**
     * Marca la tarea como estratégica.
     * Las tareas críticas no ejecutadas se reportan en KPI #8.
     */
    @Field("is_critical")
    private boolean critical;

    // ── Asignación (assignment) ──────────────────────────────────────────

    /**
     * MongoDB _id del colaborador asignado (referencia a colección "users").
     * Indexado para O(n log n) lookup del portfolio de tareas de un usuario.
     */
    @Indexed
    @Field("assigned_user_id")
    private String assignedUserId;

    /**
     * Puesto del colaborador en el momento de la asignación.
     * Desnormalizado para mantener reportes históricos coherentes (Obj. #11)
     * aunque el puesto del usuario cambie en el futuro.
     */
    @Field("position")
    private String position;

    /**
     * ID de la sucursal donde se ejecuta la tarea.
     * Referencia a la futura colección "stores" (Sprint 7).
     */
    @Indexed
    @Field("store_id")
    private String storeId;

    /**
     * Turno en que debe ejecutarse: MATUTINO | VESPERTINO | NOCTURNO.
     * String para flexibilidad con turnos custom (Obj. #15).
     */
    @Field("shift")
    private String shift;

    /**
     * Fecha y hora límite de ejecución.
     * Determina el cálculo automático de {@link Execution#onTime} (KPI #1).
     */
    @Field("due_at")
    private Instant dueAt;

    // ── Procesos (checklist por tags) ─────────────────────────────────────

    @Builder.Default
    @Field("processes")
    private List<ProcessStep> processes = new ArrayList<>();

    // ── Recurrencia ───────────────────────────────────────────────────────

    /**
     * Indica si la tarea se repite periódicamente.
     * Cuando {@code true}, los campos {@code recurrenceDays},
     * {@code recurrenceStartTime} y {@code recurrenceEndTime} son obligatorios.
     */
    @Builder.Default
    @Field("is_recurring")
    private boolean recurring = false;

    /**
     * Días de la semana en que se repite: LUN, MAR, MIE, JUE, VIE, SAB, DOM.
     */
    @Builder.Default
    @Field("recurrence_days")
    private List<String> recurrenceDays = new ArrayList<>();

    /** Hora de inicio de la tarea recurrente (formato HH:mm, ej. "08:00"). */
    @Field("recurrence_start_time")
    private String recurrenceStartTime;

    /** Hora de fin de la tarea recurrente (formato HH:mm, ej. "17:00"). */
    @Field("recurrence_end_time")
    private String recurrenceEndTime;

    // ── Ejecución (execution) ────────────────────────────────────────────

    /**
     * Sub-documento con el ciclo de vida operativo completo.
     * Inicializado automáticamente con status PENDING al construir la entidad.
     */
    @Builder.Default
    @Field("execution")
    private Execution execution = Execution.builder().build();

    // ── Auditoría (audit) ────────────────────────────────────────────────

    /**
     * Número de veces que la tarea fue devuelta por mala ejecución.
     * Se incrementa al marcar FAILED. Alimenta el KPI #3 (Tasa de Re-trabajo).
     */
    @Builder.Default
    @Field("rework_count")
    private int reworkCount = 0;

    /**
     * Calificación de calidad de 1.0 a 5.0, asignada por GERENTE/ADMIN.
     * Null hasta evaluación. Alimenta el IGEO (KPI #10: componente Calidad).
     */
    @Field("quality_rating")
    private Double qualityRating;

    /** Comentarios del evaluador o causa de fallo al marcar FAILED. */
    @Field("comments")
    private String comments;

    // ── Meta / Trazabilidad ──────────────────────────────────────────────

    /**
     * Soft-delete. Preserva datos históricos para KPIs sin borrar documentos (Obj. #11).
     * Una tarea "eliminada" en la UI solo se marca activo=false.
     */
    @Builder.Default
    @Field("activo")
    private boolean activo = true;

    /** MongoDB _id del usuario (ADMIN/GERENTE) que creó la tarea. */
    @Field("created_by")
    private String createdBy;

    /**
     * Historial de transiciones de estado.
     * Alimenta el KPI #9 (Velocidad de Corrección): permite medir el tiempo
     * entre FAILED y la siguiente COMPLETED en ciclos de re-trabajo.
     */
    @Builder.Default
    @Field("transitions")
    private List<StatusTransition> transitions = new ArrayList<>();

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
