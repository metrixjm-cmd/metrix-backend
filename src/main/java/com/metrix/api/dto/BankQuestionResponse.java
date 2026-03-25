package com.metrix.api.dto;

import com.metrix.api.model.QuestionDifficulty;
import com.metrix.api.model.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class BankQuestionResponse {

    private String           id;
    private Long             version;

    private String           questionText;
    private QuestionType     type;
    private List<String>     options;

    // Solo visible para ADMIN/GERENTE (no se expone al ejecutador en /take)
    private int              correctOptionIndex;
    private List<Integer>    correctOptionIndexes;
    private List<String>     acceptedKeywords;

    private String           explanation;
    private int              points;
    private String           category;
    private QuestionDifficulty difficulty;
    private List<String>     tags;

    private String           createdBy;
    private String           creatorName;
    private String           storeId;
    private int              usageCount;

    private Instant          createdAt;
    private Instant          updatedAt;
}
