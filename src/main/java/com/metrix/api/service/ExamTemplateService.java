package com.metrix.api.service;

import com.metrix.api.dto.CreateExamTemplateRequest;
import com.metrix.api.dto.ExamTemplateResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ExamTemplateService {

    ExamTemplateResponse create(CreateExamTemplateRequest request, String creatorNumeroUsuario);

    Page<ExamTemplateResponse> list(String category, String tag, String storeId, int page, int size);

    ExamTemplateResponse getById(String id);

    /** Listado resumido para el selector "Crear examen desde plantilla". */
    List<ExamTemplateResponse> getSummaries();

    ExamTemplateResponse update(String id, CreateExamTemplateRequest request);

    void delete(String id);

    /** Incrementa timesUsed al crear un Exam desde esta plantilla. */
    void incrementTimesUsed(String templateId);
}
