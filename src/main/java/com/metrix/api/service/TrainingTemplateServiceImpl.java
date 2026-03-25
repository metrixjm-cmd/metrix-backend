package com.metrix.api.service;

import com.metrix.api.dto.*;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.*;
import com.metrix.api.repository.TrainingMaterialRepository;
import com.metrix.api.repository.TrainingTemplateRepository;
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
public class TrainingTemplateServiceImpl implements TrainingTemplateService {

    private final TrainingTemplateRepository templateRepository;
    private final TrainingMaterialRepository materialRepository;
    private final UserRepository             userRepository;
    private final TrainingMaterialService    materialService;

    // ── Crear ──────────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "templateSummaries", allEntries = true)
    public TrainingTemplateResponse create(CreateTrainingTemplateRequest req,
                                           String creatorNumeroUsuario) {
        User creator = userRepository.findByNumeroUsuario(creatorNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + creatorNumeroUsuario));

        validateMaterialIds(req.getMaterials().stream()
                .map(TemplateMaterialRequest::getMaterialId).toList());

        List<TemplateMaterial> materials = req.getMaterials().stream()
                .map(r -> TemplateMaterial.builder()
                        .materialId(r.getMaterialId())
                        .order(r.getOrder())
                        .required(r.isRequired())
                        .notes(r.getNotes())
                        .build())
                .sorted(Comparator.comparingInt(TemplateMaterial::getOrder))
                .toList();

        TrainingTemplate template = TrainingTemplate.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory())
                .level(req.getLevel())
                .durationHours(req.getDurationHours())
                .minPassGrade(req.getMinPassGrade())
                .materials(new ArrayList<>(materials))
                .tags(req.getTags() != null ? req.getTags() : new ArrayList<>())
                .createdBy(creator.getNumeroUsuario())
                .creatorName(creator.getNombre())
                .build();

        TrainingTemplate saved = templateRepository.save(template);

        // Incrementar usageCount de cada material referenciado
        saved.getMaterials().forEach(m -> materialService.incrementUsage(m.getMaterialId()));

        return toResponse(saved, resolveMaterialMap(saved.getMaterials()));
    }

    // ── Consultas ──────────────────────────────────────────────────────────

    @Override
    public Page<TrainingTemplateResponse> list(String category, TrainingLevel level,
                                               String tag, int page, int size) {
        int safeSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TrainingTemplate> result;
        if (tag != null) {
            result = templateRepository.findByTagsContainingAndActivoTrue(tag, pageable);
        } else if (category != null && level != null) {
            result = templateRepository.findByCategoryAndLevelAndActivoTrue(category, level, pageable);
        } else if (category != null) {
            result = templateRepository.findByCategoryAndActivoTrue(category, pageable);
        } else if (level != null) {
            result = templateRepository.findByLevelAndActivoTrue(level, pageable);
        } else {
            result = templateRepository.findByActivoTrue(pageable);
        }

        return result.map(t -> toResponse(t, resolveMaterialMap(t.getMaterials())));
    }

    @Override
    public TrainingTemplateResponse getById(String id) {
        TrainingTemplate t = templateRepository.findById(id)
                .filter(TrainingTemplate::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla no encontrada: " + id));
        return toResponse(t, resolveMaterialMap(t.getMaterials()));
    }

    @Override
    @Cacheable("templateSummaries")
    public List<TrainingTemplateResponse> getSummaries() {
        return templateRepository.findSummaries().stream()
                .map(t -> toResponse(t, Map.of()))
                .toList();
    }

    // ── Actualizar ─────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "templateSummaries", allEntries = true)
    public TrainingTemplateResponse update(String id, CreateTrainingTemplateRequest req,
                                           String editorNumeroUsuario) {
        TrainingTemplate template = templateRepository.findById(id)
                .filter(TrainingTemplate::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla no encontrada: " + id));

        List<String> oldIds = template.getMaterials().stream()
                .map(TemplateMaterial::getMaterialId).toList();
        List<String> newIds = req.getMaterials().stream()
                .map(TemplateMaterialRequest::getMaterialId).toList();

        validateMaterialIds(newIds);

        // Decrementar materiales quitados, incrementar los nuevos
        Set<String> oldSet = new HashSet<>(oldIds);
        Set<String> newSet = new HashSet<>(newIds);

        oldSet.stream().filter(mid -> !newSet.contains(mid))
              .forEach(materialService::decrementUsage);
        newSet.stream().filter(mid -> !oldSet.contains(mid))
              .forEach(materialService::incrementUsage);

        List<TemplateMaterial> materials = req.getMaterials().stream()
                .map(r -> TemplateMaterial.builder()
                        .materialId(r.getMaterialId())
                        .order(r.getOrder())
                        .required(r.isRequired())
                        .notes(r.getNotes())
                        .build())
                .sorted(Comparator.comparingInt(TemplateMaterial::getOrder))
                .toList();

        template.setTitle(req.getTitle());
        template.setDescription(req.getDescription());
        template.setCategory(req.getCategory());
        template.setLevel(req.getLevel());
        template.setDurationHours(req.getDurationHours());
        template.setMinPassGrade(req.getMinPassGrade());
        template.setMaterials(new ArrayList<>(materials));
        template.setTags(req.getTags() != null ? req.getTags() : new ArrayList<>());

        TrainingTemplate saved = templateRepository.save(template);
        return toResponse(saved, resolveMaterialMap(saved.getMaterials()));
    }

    // ── Eliminar ───────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "templateSummaries", allEntries = true)
    public void delete(String id) {
        TrainingTemplate template = templateRepository.findById(id)
                .filter(TrainingTemplate::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla no encontrada: " + id));

        // Decrementar usageCount de todos los materiales referenciados
        template.getMaterials().forEach(m -> materialService.decrementUsage(m.getMaterialId()));

        template.setActivo(false);
        templateRepository.save(template);
    }

    // ── Métrica ────────────────────────────────────────────────────────────

    @Override
    public void incrementTimesUsed(String templateId) {
        templateRepository.findById(templateId).ifPresent(t -> {
            t.setTimesUsed(t.getTimesUsed() + 1);
            templateRepository.save(t);
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void validateMaterialIds(List<String> ids) {
        ids.forEach(id -> {
            if (!materialRepository.existsById(id)) {
                throw new ResourceNotFoundException("Material no encontrado en el banco: " + id);
            }
        });
    }

    /**
     * Carga los TrainingMaterial referenciados en lote (1 query)
     * y los devuelve como mapa materialId → TrainingMaterial.
     */
    private Map<String, TrainingMaterial> resolveMaterialMap(List<TemplateMaterial> items) {
        if (items == null || items.isEmpty()) return Map.of();
        List<String> ids = items.stream().map(TemplateMaterial::getMaterialId).toList();
        return materialRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(TrainingMaterial::getId, Function.identity()));
    }

    private TrainingTemplateResponse toResponse(TrainingTemplate t,
                                                Map<String, TrainingMaterial> materialMap) {
        List<TemplateMaterialResponse> resolvedMaterials = t.getMaterials().stream()
                .map(tm -> {
                    TrainingMaterial m = materialMap.get(tm.getMaterialId());
                    var builder = TemplateMaterialResponse.builder()
                            .materialId(tm.getMaterialId())
                            .order(tm.getOrder())
                            .required(tm.isRequired())
                            .notes(tm.getNotes());
                    if (m != null) {
                        builder.title(m.getTitle())
                               .description(m.getDescription())
                               .type(m.getType())
                               .url(m.getUrl())
                               .originalFileName(m.getOriginalFileName())
                               .fileSizeBytes(m.getFileSizeBytes())
                               .mimeType(m.getMimeType())
                               .category(m.getCategory())
                               .tags(m.getTags());
                    }
                    return builder.build();
                })
                .toList();

        return TrainingTemplateResponse.builder()
                .id(t.getId())
                .version(t.getVersion())
                .title(t.getTitle())
                .description(t.getDescription())
                .category(t.getCategory())
                .level(t.getLevel())
                .durationHours(t.getDurationHours())
                .minPassGrade(t.getMinPassGrade())
                .materials(resolvedMaterials)
                .tags(t.getTags())
                .createdBy(t.getCreatedBy())
                .creatorName(t.getCreatorName())
                .timesUsed(t.getTimesUsed())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
