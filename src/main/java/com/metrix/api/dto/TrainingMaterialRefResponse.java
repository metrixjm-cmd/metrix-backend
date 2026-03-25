package com.metrix.api.dto;

import com.metrix.api.model.MaterialType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** Material resuelto dentro de una Training — con tracking de visualización. */
@Data
@Builder
public class TrainingMaterialRefResponse {

    // Del sub-documento TrainingMaterialRef
    private String  materialId;
    private int     order;
    private boolean required;
    private String  notes;
    private boolean viewed;
    private Instant viewedAt;

    // Del TrainingMaterial referenciado (banco)
    private String       title;
    private String       description;
    private MaterialType type;
    private String       url;
    private String       originalFileName;
    private Long         fileSizeBytes;
    private String       mimeType;
    private String       category;
    private List<String> tags;
}
