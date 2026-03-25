package com.metrix.api.service;

import com.metrix.api.dto.CreateLinkMaterialRequest;
import com.metrix.api.dto.TrainingMaterialResponse;
import com.metrix.api.model.MaterialType;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TrainingMaterialService {

    /** Sube un archivo (PDF, VIDEO, IMAGE) y crea el material en el banco. */
    TrainingMaterialResponse uploadFile(MultipartFile file, String title, String description,
                                        String category, List<String> tags,
                                        String storeId, String uploaderNumeroUsuario);

    /** Crea un material de tipo LINK (sin archivo). */
    TrainingMaterialResponse createLink(CreateLinkMaterialRequest request, String uploaderNumeroUsuario);

    /** Listado paginado con filtros opcionales. */
    Page<TrainingMaterialResponse> list(MaterialType type, String category,
                                        String tag, String storeId, int page, int size);

    /** Detalle por ID. */
    TrainingMaterialResponse getById(String id);

    /** Tags únicos para autocompletado. */
    List<String> getAllTags();

    /** Incrementar usageCount (llamado al referenciar el material). */
    void incrementUsage(String materialId);

    /** Decrementar usageCount (llamado al quitar referencia). */
    void decrementUsage(String materialId);

    /** Soft-delete. Solo permitido si usageCount == 0. */
    void delete(String id);
}
