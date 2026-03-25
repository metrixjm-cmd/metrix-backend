package com.metrix.api.service;

import com.metrix.api.dto.*;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.*;
import com.metrix.api.repository.BankQuestionRepository;
import com.metrix.api.repository.ExamTemplateRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExamTemplateServiceImpl implements ExamTemplateService {

    private final ExamTemplateRepository templateRepo;
    private final BankQuestionRepository questionRepo;
    private final UserRepository         userRepo;

    // ── Crear ──────────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "examTemplateSummaries", allEntries = true)
    public ExamTemplateResponse create(CreateExamTemplateRequest req, String creatorNumeroUsuario) {
        User creator = userRepo.findByNumeroUsuario(creatorNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + creatorNumeroUsuario));

        validateQuestionIds(req.getQuestions().stream()
                .map(ExamTemplateQuestionRequest::getQuestionId).toList());

        List<ExamTemplateQuestion> questions = buildSortedQuestions(req.getQuestions());

        ExamTemplate template = ExamTemplate.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory())
                .passingScore(req.getPassingScore() > 0 ? req.getPassingScore() : 70)
                .timeLimitMinutes(req.getTimeLimitMinutes())
                .shuffleQuestions(req.isShuffleQuestions())
                .shuffleOptions(req.isShuffleOptions())
                .maxAttempts(req.getMaxAttempts())
                .questions(questions)
                .tags(req.getTags() != null ? req.getTags() : new ArrayList<>())
                .createdBy(creator.getNumeroUsuario())
                .creatorName(creator.getNombre())
                .storeId(req.getStoreId())
                .build();

        ExamTemplate saved = templateRepo.save(template);
        return toResponse(saved, resolveQuestionMap(saved.getQuestions()));
    }

    // ── Consultas ──────────────────────────────────────────────────────────

    @Override
    public Page<ExamTemplateResponse> list(String category, String tag, String storeId,
                                           int page, int size) {
        int safeSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ExamTemplate> result;
        if (storeId != null) {
            result = templateRepo.findVisibleForStore(storeId, pageable);
        } else if (tag != null) {
            result = templateRepo.findByTagsContainingAndActivoTrue(tag, pageable);
        } else if (category != null) {
            result = templateRepo.findByCategoryAndActivoTrue(category, pageable);
        } else {
            result = templateRepo.findByActivoTrue(pageable);
        }

        return result.map(t -> toResponse(t, resolveQuestionMap(t.getQuestions())));
    }

    @Override
    public ExamTemplateResponse getById(String id) {
        ExamTemplate t = templateRepo.findById(id)
                .filter(ExamTemplate::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla de examen no encontrada: " + id));
        return toResponse(t, resolveQuestionMap(t.getQuestions()));
    }

    @Override
    @Cacheable("examTemplateSummaries")
    public List<ExamTemplateResponse> getSummaries() {
        return templateRepo.findSummaries().stream()
                .map(t -> toResponse(t, Map.of()))
                .toList();
    }

    // ── Actualizar ─────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "examTemplateSummaries", allEntries = true)
    public ExamTemplateResponse update(String id, CreateExamTemplateRequest req) {
        ExamTemplate template = templateRepo.findById(id)
                .filter(ExamTemplate::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla no encontrada: " + id));

        validateQuestionIds(req.getQuestions().stream()
                .map(ExamTemplateQuestionRequest::getQuestionId).toList());

        template.setTitle(req.getTitle());
        template.setDescription(req.getDescription());
        template.setCategory(req.getCategory());
        template.setPassingScore(req.getPassingScore() > 0 ? req.getPassingScore() : 70);
        template.setTimeLimitMinutes(req.getTimeLimitMinutes());
        template.setShuffleQuestions(req.isShuffleQuestions());
        template.setShuffleOptions(req.isShuffleOptions());
        template.setMaxAttempts(req.getMaxAttempts());
        template.setQuestions(buildSortedQuestions(req.getQuestions()));
        template.setTags(req.getTags() != null ? req.getTags() : new ArrayList<>());

        ExamTemplate saved = templateRepo.save(template);
        return toResponse(saved, resolveQuestionMap(saved.getQuestions()));
    }

    // ── Eliminar ───────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "examTemplateSummaries", allEntries = true)
    public void delete(String id) {
        ExamTemplate template = templateRepo.findById(id)
                .filter(ExamTemplate::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla no encontrada: " + id));
        template.setActivo(false);
        templateRepo.save(template);
    }

    // ── Métrica ────────────────────────────────────────────────────────────

    @Override
    public void incrementTimesUsed(String templateId) {
        templateRepo.findById(templateId).ifPresent(t -> {
            t.setTimesUsed(t.getTimesUsed() + 1);
            templateRepo.save(t);
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void validateQuestionIds(List<String> ids) {
        ids.forEach(id -> {
            if (!questionRepo.existsById(id)) {
                throw new ResourceNotFoundException(
                        "Pregunta del banco no encontrada: " + id);
            }
        });
    }

    private List<ExamTemplateQuestion> buildSortedQuestions(
            List<ExamTemplateQuestionRequest> reqs) {
        return reqs.stream()
                .map(r -> ExamTemplateQuestion.builder()
                        .questionId(r.getQuestionId())
                        .order(r.getOrder())
                        .pointsOverride(r.getPointsOverride())
                        .build())
                .sorted(Comparator.comparingInt(ExamTemplateQuestion::getOrder))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Carga las BankQuestions en 1 query y devuelve mapa questionId → BankQuestion. */
    private Map<String, BankQuestion> resolveQuestionMap(List<ExamTemplateQuestion> items) {
        if (items == null || items.isEmpty()) return Map.of();
        List<String> ids = items.stream().map(ExamTemplateQuestion::getQuestionId).toList();
        return questionRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(BankQuestion::getId, Function.identity()));
    }

    private ExamTemplateResponse toResponse(ExamTemplate t,
                                             Map<String, BankQuestion> questionMap) {
        List<ExamTemplateQuestionResponse> resolved = (t.getQuestions() == null)
                ? List.of()
                : t.getQuestions().stream()
                        .map(tq -> {
                            BankQuestion bq = questionMap.get(tq.getQuestionId());
                            int effectivePoints = tq.getPointsOverride() > 0
                                    ? tq.getPointsOverride()
                                    : (bq != null ? bq.getPoints() : 1);

                            var b = ExamTemplateQuestionResponse.builder()
                                    .questionId(tq.getQuestionId())
                                    .order(tq.getOrder())
                                    .pointsOverride(tq.getPointsOverride())
                                    .points(effectivePoints);

                            if (bq != null) {
                                b.questionText(bq.getQuestionText())
                                 .type(bq.getType())
                                 .options(bq.getOptions())
                                 .correctOptionIndex(bq.getCorrectOptionIndex())
                                 .correctOptionIndexes(bq.getCorrectOptionIndexes())
                                 .acceptedKeywords(bq.getAcceptedKeywords())
                                 .explanation(bq.getExplanation())
                                 .category(bq.getCategory())
                                 .difficulty(bq.getDifficulty())
                                 .tags(bq.getTags())
                                 .usageCount(bq.getUsageCount());
                            }
                            return b.build();
                        })
                        .toList();

        return ExamTemplateResponse.builder()
                .id(t.getId())
                .version(t.getVersion())
                .title(t.getTitle())
                .description(t.getDescription())
                .category(t.getCategory())
                .passingScore(t.getPassingScore())
                .timeLimitMinutes(t.getTimeLimitMinutes())
                .shuffleQuestions(t.isShuffleQuestions())
                .shuffleOptions(t.isShuffleOptions())
                .maxAttempts(t.getMaxAttempts())
                .questions(resolved)
                .tags(t.getTags())
                .createdBy(t.getCreatedBy())
                .creatorName(t.getCreatorName())
                .storeId(t.getStoreId())
                .timesUsed(t.getTimesUsed())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
