package com.metrix.api.dto;

import com.metrix.api.model.QuestionDifficulty;
import com.metrix.api.model.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Pregunta resuelta dentro de una plantilla de examen. */
@Data
@Builder
public class ExamTemplateQuestionResponse {

    // Del sub-documento ExamTemplateQuestion
    private String questionId;
    private int    order;
    private int    pointsOverride;

    // Del BankQuestion referenciado
    private String           questionText;
    private QuestionType     type;
    private List<String>     options;
    private int              correctOptionIndex;
    private List<Integer>    correctOptionIndexes;
    private List<String>     acceptedKeywords;
    private String           explanation;
    private int              points;           // efectivos (override o banco)
    private String           category;
    private QuestionDifficulty difficulty;
    private List<String>     tags;
    private int              usageCount;
}
