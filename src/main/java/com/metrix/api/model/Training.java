package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Entidad principal de capacitación en METRIX (Sprint 10).
 * <p>
 * Patrón igual a {@link Task}: definición + asignación + progreso embedded + auditoría.
 * <p>
 * Índices compuestos optimizados para:
 * <ul>
 *   <li>{@code idx_user_status} → portfolio de capacitaciones del EJECUTADOR.</li>
 *   <li>{@code idx_store_status} → vista gerencial por sucursal y estado.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "trainings")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_user_status",
                def = "{'assigned_user_id': 1, 'progress.status': 1}"),
        @CompoundIndex(
                name = "idx_store_status",
                def = "{'store_id': 1, 'progress.status': 1}"),
})
public class Training {

    @Id
    private String id;

    // ── Definición ───────────────────────────────────────────────────────

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    /** Nivel de profundidad: BASICO | INTERMEDIO | AVANZADO. */
    @Field("level")
    private TrainingLevel level;

    /** Duración estimada en horas (1–40). */
    @Field("duration_hours")
    private int durationHours;

    /** Nota mínima aprobatoria 0.0–10.0. Determina {@link TrainingProgress#passed}. */
    @Field("min_pass_grade")
    private double minPassGrade;

    // ── Asignación ───────────────────────────────────────────────────────

    @Indexed
    @Field("assigned_user_id")
    private String assignedUserId;

    /** Puesto desnormalizado del colaborador al momento de la asignación. */
    @Field("position")
    private String position;

    @Indexed
    @Field("store_id")
    private String storeId;

    @Field("shift")
    private String shift;

    @Field("due_at")
    private Instant dueAt;

    // ── Progreso (embedded) ──────────────────────────────────────────────

    @Builder.Default
    @Field("progress")
    private TrainingProgress progress = TrainingProgress.builder().build();

    // ── Meta ─────────────────────────────────────────────────────────────

    @Builder.Default
    @Field("activo")
    private boolean activo = true;

    @Field("created_by")
    private String createdBy;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
