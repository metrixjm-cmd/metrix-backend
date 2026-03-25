package com.metrix.api.service;

import com.metrix.api.dto.CreateTrainingTemplateRequest;
import com.metrix.api.dto.TrainingTemplateResponse;
import com.metrix.api.model.TrainingLevel;
import org.springframework.data.domain.Page;

import java.util.List;

public interface TrainingTemplateService {

    TrainingTemplateResponse create(CreateTrainingTemplateRequest request, String creatorNumeroUsuario);

    Page<TrainingTemplateResponse> list(String category, TrainingLevel level, String tag, int page, int size);

    TrainingTemplateResponse getById(String id);

    /** Listado resumido para el selector "Crear desde plantilla". */
    List<TrainingTemplateResponse> getSummaries();

    TrainingTemplateResponse update(String id, CreateTrainingTemplateRequest request, String editorNumeroUsuario);

    void delete(String id);

    /** Incrementar timesUsed al crear Training desde esta plantilla. */
    void incrementTimesUsed(String templateId);
}
