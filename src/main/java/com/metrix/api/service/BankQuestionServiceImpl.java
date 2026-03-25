package com.metrix.api.service;

import com.metrix.api.dto.BankQuestionResponse;
import com.metrix.api.dto.CreateBankQuestionRequest;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.*;
import com.metrix.api.repository.BankQuestionRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BankQuestionServiceImpl implements BankQuestionService {

    private final BankQuestionRepository questionRepo;
    private final UserRepository         userRepo;

    // ── Crear ──────────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "questionBankTags", allEntries = true)
    public BankQuestionResponse create(CreateBankQuestionRequest req, String creatorNumeroUsuario) {
        User creator = userRepo.findByNumeroUsuario(creatorNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + creatorNumeroUsuario));

        validateRequest(req);

        BankQuestion question = BankQuestion.builder()
                .questionText(req.getQuestionText())
                .type(req.getType())
                .options(req.getOptions() != null ? req.getOptions() : List.of())
                .correctOptionIndex(req.getCorrectOptionIndex())
                .correctOptionIndexes(req.getCorrectOptionIndexes() != null
                        ? req.getCorrectOptionIndexes() : List.of())
                .acceptedKeywords(req.getAcceptedKeywords() != null
                        ? req.getAcceptedKeywords() : List.of())
                .explanation(req.getExplanation())
                .points(req.getPoints() > 0 ? req.getPoints() : 1)
                .category(req.getCategory())
                .difficulty(req.getDifficulty())
                .tags(req.getTags() != null ? req.getTags() : List.of())
                .createdBy(creator.getNumeroUsuario())
                .creatorName(creator.getNombre())
                .storeId(req.getStoreId())
                .build();

        return toResponse(questionRepo.save(question));
    }

    // ── Consultas ──────────────────────────────────────────────────────────

    @Override
    public Page<BankQuestionResponse> list(QuestionType type, String category,
                                           QuestionDifficulty difficulty, String tag,
                                           String storeId, int page, int size) {
        int safeSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<BankQuestion> result;

        if (storeId != null) {
            result = questionRepo.findVisibleForStore(storeId, pageable);
        } else if (tag != null) {
            result = questionRepo.findByTagsContainingAndActivoTrue(tag, pageable);
        } else if (type != null && category != null) {
            result = questionRepo.findByTypeAndCategoryAndActivoTrue(type, category, pageable);
        } else if (type != null) {
            result = questionRepo.findByTypeAndActivoTrue(type, pageable);
        } else if (category != null) {
            result = questionRepo.findByCategoryAndActivoTrue(category, pageable);
        } else if (difficulty != null) {
            result = questionRepo.findByDifficultyAndActivoTrue(difficulty, pageable);
        } else {
            result = questionRepo.findByActivoTrue(pageable);
        }

        return result.map(this::toResponse);
    }

    @Override
    public BankQuestionResponse getById(String id) {
        return questionRepo.findById(id)
                .filter(BankQuestion::isActivo)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pregunta no encontrada: " + id));
    }

    @Override
    @Cacheable("questionBankTags")
    public List<String> getAllTags() {
        return questionRepo.findAllForTags().stream()
                .flatMap(q -> q.getTags().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // ── Actualizar ─────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "questionBankTags", allEntries = true)
    public BankQuestionResponse update(String id, CreateBankQuestionRequest req) {
        BankQuestion question = questionRepo.findById(id)
                .filter(BankQuestion::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pregunta no encontrada: " + id));

        if (question.getUsageCount() > 0) {
            throw new IllegalStateException(
                    "No se puede editar una pregunta que ya está en uso en " +
                    question.getUsageCount() + " examen(es). Crea una nueva versión.");
        }

        validateRequest(req);

        question.setQuestionText(req.getQuestionText());
        question.setType(req.getType());
        question.setOptions(req.getOptions() != null ? req.getOptions() : List.of());
        question.setCorrectOptionIndex(req.getCorrectOptionIndex());
        question.setCorrectOptionIndexes(req.getCorrectOptionIndexes() != null
                ? req.getCorrectOptionIndexes() : List.of());
        question.setAcceptedKeywords(req.getAcceptedKeywords() != null
                ? req.getAcceptedKeywords() : List.of());
        question.setExplanation(req.getExplanation());
        question.setPoints(req.getPoints() > 0 ? req.getPoints() : 1);
        question.setCategory(req.getCategory());
        question.setDifficulty(req.getDifficulty());
        question.setTags(req.getTags() != null ? req.getTags() : List.of());

        return toResponse(questionRepo.save(question));
    }

    // ── usageCount ─────────────────────────────────────────────────────────

    @Override
    public void incrementUsage(String questionId) {
        questionRepo.findById(questionId).ifPresent(q -> {
            q.setUsageCount(q.getUsageCount() + 1);
            questionRepo.save(q);
        });
    }

    // ── Soft-delete ────────────────────────────────────────────────────────

    @Override
    public void delete(String id) {
        BankQuestion question = questionRepo.findById(id)
                .filter(BankQuestion::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pregunta no encontrada: " + id));

        if (question.getUsageCount() > 0) {
            throw new IllegalStateException(
                    "No se puede eliminar la pregunta porque está en uso en " +
                    question.getUsageCount() + " examen(es).");
        }

        question.setActivo(false);
        questionRepo.save(question);
    }

    // ── Validaciones ───────────────────────────────────────────────────────

    private void validateRequest(CreateBankQuestionRequest req) {
        switch (req.getType()) {
            case MULTIPLE_CHOICE, TRUE_FALSE -> {
                if (req.getOptions() == null || req.getOptions().size() < 2) {
                    throw new IllegalArgumentException(
                            "MULTIPLE_CHOICE y TRUE_FALSE requieren al menos 2 opciones.");
                }
            }
            case MULTI_SELECT -> {
                if (req.getOptions() == null || req.getOptions().size() < 2) {
                    throw new IllegalArgumentException(
                            "MULTI_SELECT requiere al menos 2 opciones.");
                }
                if (req.getCorrectOptionIndexes() == null || req.getCorrectOptionIndexes().isEmpty()) {
                    throw new IllegalArgumentException(
                            "MULTI_SELECT requiere al menos una opción correcta en correctOptionIndexes.");
                }
            }
            case OPEN_TEXT -> {
                if (req.getAcceptedKeywords() == null || req.getAcceptedKeywords().isEmpty()) {
                    throw new IllegalArgumentException(
                            "OPEN_TEXT requiere al menos una palabra clave en acceptedKeywords.");
                }
            }
        }
    }

    // ── Mapper ─────────────────────────────────────────────────────────────

    private BankQuestionResponse toResponse(BankQuestion q) {
        return BankQuestionResponse.builder()
                .id(q.getId())
                .version(q.getVersion())
                .questionText(q.getQuestionText())
                .type(q.getType())
                .options(q.getOptions())
                .correctOptionIndex(q.getCorrectOptionIndex())
                .correctOptionIndexes(q.getCorrectOptionIndexes())
                .acceptedKeywords(q.getAcceptedKeywords())
                .explanation(q.getExplanation())
                .points(q.getPoints())
                .category(q.getCategory())
                .difficulty(q.getDifficulty())
                .tags(q.getTags())
                .createdBy(q.getCreatedBy())
                .creatorName(q.getCreatorName())
                .storeId(q.getStoreId())
                .usageCount(q.getUsageCount())
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }
}
