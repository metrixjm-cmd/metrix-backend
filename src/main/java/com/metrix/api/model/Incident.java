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
 * Incidencia operativa registrada durante la ejecución en METRIX.
 * <p>
 * Cubre el Objetivo #20 — Gestión de Contingencias: resolución y registro
 * de incidencias durante la operación.
 * <p>
 * Ciclo de vida: ABIERTA → EN_RESOLUCION → CERRADA (re-apertura permitida).
 * <p>
 * Índices compuestos optimizados para los casos de uso críticos:
 * <ul>
 *   <li>{@code idx_reporter_status} → portfolio del colaborador reportador.</li>
 *   <li>{@code idx_store_status}    → vista gerencial por sucursal y estado.</li>
 *   <li>{@code idx_store_severity}  → alertas de incidencias críticas por sucursal.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "incidents")
@CompoundIndexes({
        @CompoundIndex(name = "idx_reporter_status", def = "{'reporter_user_id': 1, 'status': 1}"),
        @CompoundIndex(name = "idx_store_status",    def = "{'store_id': 1, 'status': 1}"),
        @CompoundIndex(name = "idx_store_severity",  def = "{'store_id': 1, 'severity': 1}")
})
public class Incident {

    @Id
    private String id;

    @Version
    private Long version;

    // ── Definición ──────────────────────────────────────────────────────────

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Field("category")
    private IncidentCategory category;

    @Field("severity")
    private IncidentSeverity severity;

    // ── Vínculo opcional a tarea ─────────────────────────────────────────────

    /** MongoDB _id de la tarea relacionada. Null si la incidencia es independiente. */
    @Field("task_id")
    private String taskId;

    // ── Origen / Reporte ─────────────────────────────────────────────────────

    /** MongoDB _id del usuario que reporta la incidencia. */
    @Indexed
    @Field("reporter_user_id")
    private String reporterUserId;

    /** Nombre del reportador desnormalizado para mantener reportes históricos coherentes (Obj. #11). */
    @Field("reporter_name")
    private String reporterName;

    /** Puesto del reportador desnormalizado. */
    @Field("reporter_position")
    private String reporterPosition;

    /** Rol principal del reportador desnormalizado (ADMIN, GERENTE, EJECUTADOR). */
    @Indexed
    @Field("reporter_role")
    private String reporterRole;

    @Indexed
    @Field("store_id")
    private String storeId;

    @Field("shift")
    private String shift;

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    @Builder.Default
    @Field("status")
    private IncidentStatus status = IncidentStatus.ABIERTA;

    // ── Roles y Actores ──────────────────────────────────────────────────────

    /** Personas involucradas en el evento (estructura enriquecida con tipo y responsabilidad). */
    @Builder.Default
    @Field("implicados")
    private List<ImplicadoEntry> implicados = new ArrayList<>();

    /** Nombre o identificador del responsable asignado para gestionar la solución. */
    @Field("follow_up_responsible")
    private String followUpResponsible;

    // ── Resolución ───────────────────────────────────────────────────────────

    /** numeroUsuario del colaborador que cierra la incidencia. */
    @Field("resolved_by_user_id")
    private String resolvedByUserId;

    /** Nombre completo del usuario que realizó el cierre definitivo (desnormalizado). */
    @Field("closed_by_name")
    private String closedByName;

    /** Número de usuario del que realizó el cierre definitivo (desnormalizado). */
    @Field("closed_by_numero")
    private String closedByNumero;

    /** Obligatorio al mover a CERRADA. Describe la acción correctiva tomada. */
    @Field("resolution_notes")
    private String resolutionNotes;

    @Field("resolved_at")
    private Instant resolvedAt;

    // ── Evidencias ───────────────────────────────────────────────────────────

    /** URLs de evidencia (fotos/videos ya subidas a GCS u otras fuentes). */
    @Builder.Default
    @Field("evidence_urls")
    private List<String> evidenceUrls = new ArrayList<>();

    // ── Historial de transiciones ────────────────────────────────────────────

    @Builder.Default
    @Field("transitions")
    private List<IncidentTransition> transitions = new ArrayList<>();

    // ── Meta ─────────────────────────────────────────────────────────────────

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
