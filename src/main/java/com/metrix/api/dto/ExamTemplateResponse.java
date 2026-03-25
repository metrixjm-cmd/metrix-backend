package com.metrix.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ExamTemplateResponse {

    private String  id;
    private Long    version;

    private String  title;
    private String  description;
    private String  category;
    private int     passingScore;
    private Integer timeLimitMinutes;
    private boolean shuffleQuestions;
    private boolean shuffleOptions;
    private int     maxAttempts;

    /** Preguntas resueltas con datos del banco. */
    private List<ExamTemplateQuestionResponse> questions;

    private List<String> tags;

    private String  createdBy;
    private String  creatorName;
    private String  storeId;
    private int     timesUsed;

    private Instant createdAt;
    private Instant updatedAt;
}
