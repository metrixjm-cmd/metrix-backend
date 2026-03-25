package com.metrix.api.dto;

import com.metrix.api.model.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ExamResponse {

    private String id;
    private String title;
    private String description;
    private String trainingId;
    private String storeId;
    private List<QuestionDto> questions;
    private int passingScore;
    private Integer timeLimitMinutes;
    private String createdByName;
    private Instant createdAt;
    private Instant updatedAt;

    private int maxAttempts;

    /** Estadísticas calculadas al listar. */
    private long submissionCount;
    private int passRate; // 0–100

    @Data
    @Builder
    public static class QuestionDto {
        private String       id;
        private String       questionText;
        private QuestionType type;
        private List<String> options;
        private int          correctOptionIndex;
        private List<Integer> correctOptionIndexes;
        private List<String> acceptedKeywords;
        private String       explanation;
        private int          points;
    }
}
