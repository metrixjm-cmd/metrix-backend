package com.metrix.api.dto;

import com.metrix.api.model.MaterialType;
import lombok.Builder;
import lombok.Data;

/** Material resuelto dentro de una plantilla (incluye datos del banco). */
@Data
@Builder
public class TemplateMaterialResponse {

    // ── Del sub-documento TemplateMaterial ────────────────────────────────
    private String materialId;
    private int    order;
    private boolean required;
    private String notes;

    // ── Del TrainingMaterial referenciado ────────────────────────────────
    private String       title;
    private String       description;
    private MaterialType type;
    private String       url;
    private String       originalFileName;
    private Long         fileSizeBytes;
    private String       mimeType;
    private String       category;
    private java.util.List<String> tags;
}
