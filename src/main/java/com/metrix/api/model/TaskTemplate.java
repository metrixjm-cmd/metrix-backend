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
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Plantilla reutilizable de tarea — "Banco de Tareas".
 * <p>
 * Permite crear tareas recurrentes con título, descripción, categoría y
 * checklist de procesos predefinidos. Al asignar una tarea desde esta
 * plantilla los campos se pre-rellenan en el formulario; el usuario puede
 * editarlos antes de guardar.
 * <p>
 * Compatible hacia atrás: el flujo de creación manual de tareas no se ve afectado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "task_templates")
@CompoundIndexes({
        @CompoundIndex(name = "idx_ttpl_cat",    def = "{'category': 1, 'activo': 1}"),
        @CompoundIndex(name = "idx_ttpl_activo", def = "{'activo': 1, 'created_at': -1}")
})
public class TaskTemplate {

    @Id
    private String id;

    @Version
    private Long version;

    /** Título de la tarea plantilla. Indexado para búsqueda full-text. */
    @TextIndexed
    @Field("title")
    private String title;

    /** Descripción detallada que se copia al crear la tarea. */
    @Field("description")
    private String description;

    /**
     * Valor de categoría del catálogo (CATEGORIA). Referencia por valor, no por id,
     * igual que {@link Task#category} — permite cambiar el label sin romper consistencia.
     */
    @Field("category")
    private String category;

    /** Pasos de proceso predefinidos para el checklist de la tarea. */
    @Builder.Default
    @Field("steps")
    private List<TaskTemplateStep> steps = new ArrayList<>();

    /** Multimedia opcional que puede mostrarse al seleccionar la entrada. */
    @Builder.Default
    @Field("media")
    private List<TaskTemplateMedia> media = new ArrayList<>();

    /**
     * Soft-delete: false → oculta del catálogo sin borrar el histórico.
     */
    @Builder.Default
    @Field("activo")
    private boolean activo = true;

    /** MongoDB _id del usuario (ADMIN/GERENTE) que creó la plantilla. */
    @Field("created_by")
    private String createdBy;

    /** Nombre del creador (desnormalizado para evitar joins). */
    @Field("creator_name")
    private String creatorName;

    /** Cuántas veces se usó esta plantilla para crear una tarea real. */
    @Builder.Default
    @Field("times_used")
    private int timesUsed = 0;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
