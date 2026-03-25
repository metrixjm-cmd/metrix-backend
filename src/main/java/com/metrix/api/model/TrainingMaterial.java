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
 * Material reutilizable del banco de información de METRIX.
 * <p>
 * Soporta cuatro tipos de contenido: PDF, VIDEO, IMAGE y LINK.
 * Los materiales de archivo son subidos a GCS y referenciados por URL.
 * Los materiales tipo LINK almacenan solo la URL externa.
 * <p>
 * El campo {@code usageCount} se incrementa cada vez que un Training
 * o TrainingTemplate referencia este material.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "training_materials")
@CompoundIndexes({
        @CompoundIndex(name = "idx_type_category", def = "{'type': 1, 'category': 1, 'activo': 1}"),
        @CompoundIndex(name = "idx_store_type",    def = "{'store_id': 1, 'type': 1, 'activo': 1}")
})
public class TrainingMaterial {

    @Id
    private String id;

    @Version
    private Long version;

    // ── Contenido ──────────────────────────────────────────────────────────

    @Field("title")
    private String title;

    @Field("description")
    private String description;

    @Indexed
    @Field("type")
    private MaterialType type;

    /** URL GCS, URL local (fallback) o link externo (tipo LINK). */
    @Field("url")
    private String url;

    /** Nombre original del archivo. Null para LINK. */
    @Field("original_file_name")
    private String originalFileName;

    /** Tamaño en bytes. Null para LINK. */
    @Field("file_size_bytes")
    private Long fileSizeBytes;

    /** MIME type del archivo. Null para LINK. */
    @Field("mime_type")
    private String mimeType;

    // ── Organización ───────────────────────────────────────────────────────

    /** Categoría del catálogo dinámico (mismo catálogo que tareas). */
    @Indexed
    @Field("category")
    private String category;

    @Builder.Default
    @Field("tags")
    private List<String> tags = new ArrayList<>();

    // ── Autoría y alcance ──────────────────────────────────────────────────

    /** numeroUsuario de quien subió el material. */
    @Field("uploaded_by")
    private String uploadedBy;

    /** Nombre desnormalizado para reportes históricos. */
    @Field("uploader_name")
    private String uploaderName;

    /**
     * Sucursal propietaria del material.
     * Null = material global visible para todas las sucursales.
     */
    @Indexed
    @Field("store_id")
    private String storeId;

    // ── Métricas ───────────────────────────────────────────────────────────

    /** Cuántas capacitaciones y plantillas referencian este material. */
    @Builder.Default
    @Field("usage_count")
    private int usageCount = 0;

    // ── Meta ───────────────────────────────────────────────────────────────

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
