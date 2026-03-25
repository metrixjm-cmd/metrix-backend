package com.metrix.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExamTemplateQuestionRequest {

    @NotBlank
    private String questionId;

    @Min(1)
    private int order;

    /** 0 = usar los puntos del banco. */
    private int pointsOverride = 0;
}
