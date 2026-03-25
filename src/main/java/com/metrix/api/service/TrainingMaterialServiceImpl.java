package com.metrix.api.service;

import com.metrix.api.dto.CreateLinkMaterialRequest;
import com.metrix.api.dto.TrainingMaterialResponse;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.MaterialType;
import com.metrix.api.model.TrainingMaterial;
import com.metrix.api.model.User;
import com.metrix.api.repository.TrainingMaterialRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingMaterialServiceImpl implements TrainingMaterialService {

    private final TrainingMaterialRepository materialRepository;
    private final UserRepository             userRepository;
    private final GcsService                 gcsService;

    // ── Upload de archivo ──────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "materialTags", allEntries = true)
    public TrainingMaterialResponse uploadFile(MultipartFile file, String title,
                                               String description, String category,
                                               List<String> tags, String storeId,
                                               String uploaderNumeroUsuario) {
        User uploader = userRepository.findByNumeroUsuario(uploaderNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + uploaderNumeroUsuario));

        String contentType = file.getContentType() != null ? file.getContentType() : "";
        MaterialType type  = resolveMaterialType(contentType, file.getOriginalFilename());
        String extension   = resolveExtension(contentType, file.getOriginalFilename());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Error al leer el archivo.", e);
        }

        String entityId  = "material-" + System.currentTimeMillis();
        String tipoPath  = type.name().toLowerCase();
        String url       = gcsService.uploadMaterial(storeId != null ? storeId : "global",
                                                     entityId, tipoPath, bytes,
                                                     contentType, extension);

        TrainingMaterial material = TrainingMaterial.builder()
                .title(title)
                .description(description)
                .type(type)
                .url(url)
                .originalFileName(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .mimeType(contentType)
                .category(category)
                .tags(tags != null ? tags : List.of())
                .uploadedBy(uploader.getNumeroUsuario())
                .uploaderName(uploader.getNombre())
                .storeId(storeId)
                .build();

        return toResponse(materialRepository.save(material));
    }

    // ── Crear LINK ─────────────────────────────────────────────────────────

    @Override
    @CacheEvict(value = "materialTags", allEntries = true)
    public TrainingMaterialResponse createLink(CreateLinkMaterialRequest request,
                                               String uploaderNumeroUsuario) {
        User uploader = userRepository.findByNumeroUsuario(uploaderNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + uploaderNumeroUsuario));

        TrainingMaterial material = TrainingMaterial.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .type(MaterialType.LINK)
                .url(request.getUrl())
                .category(request.getCategory())
                .tags(request.getTags() != null ? request.getTags() : List.of())
                .uploadedBy(uploader.getNumeroUsuario())
                .uploaderName(uploader.getNombre())
                .storeId(request.getStoreId())
                .build();

        return toResponse(materialRepository.save(material));
    }

    // ── Consultas ──────────────────────────────────────────────────────────

    @Override
    public Page<TrainingMaterialResponse> list(MaterialType type, String category,
                                               String tag, String storeId,
                                               int page, int size) {
        int safeSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TrainingMaterial> result;

        if (storeId != null) {
            result = materialRepository.findVisibleForStore(storeId, pageable);
        } else if (tag != null) {
            result = materialRepository.findByTagsContainingAndActivoTrue(tag, pageable);
        } else if (type != null && category != null) {
            result = materialRepository.findByTypeAndCategoryAndActivoTrue(type, category, pageable);
        } else if (type != null) {
            result = materialRepository.findByTypeAndActivoTrue(type, pageable);
        } else if (category != null) {
            result = materialRepository.findByCategoryAndActivoTrue(category, pageable);
        } else {
            result = materialRepository.findByActivoTrue(pageable);
        }

        return result.map(this::toResponse);
    }

    @Override
    public TrainingMaterialResponse getById(String id) {
        return materialRepository.findById(id)
                .filter(TrainingMaterial::isActivo)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Material no encontrado: " + id));
    }

    @Override
    @Cacheable("materialTags")
    public List<String> getAllTags() {
        return materialRepository.findAllTags().stream()
                .flatMap(m -> m.getTags().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // ── Contadores de uso ──────────────────────────────────────────────────

    @Override
    public void incrementUsage(String materialId) {
        materialRepository.findById(materialId).ifPresent(m -> {
            m.setUsageCount(m.getUsageCount() + 1);
            materialRepository.save(m);
        });
    }

    @Override
    public void decrementUsage(String materialId) {
        materialRepository.findById(materialId).ifPresent(m -> {
            m.setUsageCount(Math.max(0, m.getUsageCount() - 1));
            materialRepository.save(m);
        });
    }

    // ── Soft-delete ────────────────────────────────────────────────────────

    @Override
    public void delete(String id) {
        TrainingMaterial material = materialRepository.findById(id)
                .filter(TrainingMaterial::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Material no encontrado: " + id));

        if (material.getUsageCount() > 0) {
            throw new IllegalStateException(
                    "No se puede eliminar el material porque está siendo usado en " +
                    material.getUsageCount() + " capacitación(es). " +
                    "Retíralo de las capacitaciones antes de eliminarlo.");
        }

        material.setActivo(false);
        materialRepository.save(material);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private MaterialType resolveMaterialType(String contentType, String filename) {
        if (contentType.equals("application/pdf")) return MaterialType.PDF;
        if (contentType.startsWith("video/"))       return MaterialType.VIDEO;
        if (contentType.startsWith("image/"))       return MaterialType.IMAGE;
        // Fallback por extensión
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".pdf"))                           return MaterialType.PDF;
            if (lower.endsWith(".mp4") || lower.endsWith(".webm")) return MaterialType.VIDEO;
            if (lower.endsWith(".jpg") || lower.endsWith(".png")
                    || lower.endsWith(".jpeg") || lower.endsWith(".webp")) return MaterialType.IMAGE;
        }
        throw new IllegalArgumentException(
                "Tipo de archivo no soportado. Se aceptan PDF, imágenes (JPG, PNG, WebP) y videos (MP4, WebM).");
    }

    private String resolveExtension(String contentType, String filename) {
        return switch (contentType) {
            case "application/pdf" -> "pdf";
            case "video/mp4"       -> "mp4";
            case "video/webm"      -> "webm";
            case "image/jpeg"      -> "jpg";
            case "image/png"       -> "png";
            case "image/webp"      -> "webp";
            default -> {
                if (filename != null && filename.contains(".")) {
                    yield filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
                }
                yield "bin";
            }
        };
    }

    private TrainingMaterialResponse toResponse(TrainingMaterial m) {
        return TrainingMaterialResponse.builder()
                .id(m.getId())
                .version(m.getVersion())
                .title(m.getTitle())
                .description(m.getDescription())
                .type(m.getType())
                .url(m.getUrl())
                .originalFileName(m.getOriginalFileName())
                .fileSizeBytes(m.getFileSizeBytes())
                .mimeType(m.getMimeType())
                .category(m.getCategory())
                .tags(m.getTags())
                .uploadedBy(m.getUploadedBy())
                .uploaderName(m.getUploaderName())
                .storeId(m.getStoreId())
                .usageCount(m.getUsageCount())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
