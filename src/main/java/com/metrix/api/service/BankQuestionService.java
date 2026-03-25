package com.metrix.api.service;

import com.metrix.api.dto.BankQuestionResponse;
import com.metrix.api.dto.CreateBankQuestionRequest;
import com.metrix.api.model.QuestionDifficulty;
import com.metrix.api.model.QuestionType;
import org.springframework.data.domain.Page;

import java.util.List;

public interface BankQuestionService {

    BankQuestionResponse create(CreateBankQuestionRequest request, String creatorNumeroUsuario);

    Page<BankQuestionResponse> list(QuestionType type, String category,
                                    QuestionDifficulty difficulty, String tag,
                                    String storeId, int page, int size);

    BankQuestionResponse getById(String id);

    BankQuestionResponse update(String id, CreateBankQuestionRequest request);

    List<String> getAllTags();

    /** Incrementa usageCount cuando un examen incluye esta pregunta como snapshot. */
    void incrementUsage(String questionId);

    /** Soft-delete. Solo permitido si usageCount == 0. */
    void delete(String id);
}
