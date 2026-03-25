package com.metrix.api.dto;

import com.metrix.api.model.TrainingLevel;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class TrainingTemplateResponse {

    private String        id;
    private Long          version;

    private String        title;
    private String        description;
    private String        category;
    private TrainingLevel level;
    private int           durationHours;
    private double        minPassGrade;

    /** Materiales resueltos (con datos del banco). */
    private List<TemplateMaterialResponse> materials;

    private List<String>  tags;

    private String        createdBy;
    private String        creatorName;
    private int           timesUsed;

    private Instant       createdAt;
    private Instant       updatedAt;
}
