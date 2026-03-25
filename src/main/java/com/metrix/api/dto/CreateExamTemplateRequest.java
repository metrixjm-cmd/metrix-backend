package com.metrix.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateExamTemplateRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    private String description;

    private String category;

    @Min(0) @Max(100)
    private int passingScore = 70;

    @Min(1)
    private Integer timeLimitMinutes;

    private boolean shuffleQuestions = false;

    private boolean shuffleOptions   = false;

    @Min(0)
    private int maxAttempts = 0;

    @Valid
    @Size(min = 1, message = "La plantilla debe tener al menos una pregunta.")
    private List<ExamTemplateQuestionRequest> questions = new ArrayList<>();

    @Size(max = 10)
    private List<String> tags = new ArrayList<>();

    private String storeId;
}
