package com.metrix.api.dto;

import com.metrix.api.model.MaterialType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class TrainingMaterialResponse {

    private String id;
    private Long version;

    private String title;
    private String description;
    private MaterialType type;
    private String url;
    private String originalFileName;
    private Long fileSizeBytes;
    private String mimeType;

    private String category;
    private List<String> tags;

    private String uploadedBy;
    private String uploaderName;
    private String storeId;

    private int usageCount;

    private Instant createdAt;
    private Instant updatedAt;
}
