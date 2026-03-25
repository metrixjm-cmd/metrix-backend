package com.metrix.api.service;

import com.metrix.api.dto.CreateTrainingRequest;
import com.metrix.api.dto.TrainingResponse;
import com.metrix.api.dto.UpdateTrainingProgressRequest;

import org.springframework.data.domain.Page;
import java.util.List;
import java.time.Instant;

/**
 * Contrato del módulo de Capacitación — Sprint 10.
 * <p>
 * Sigue el mismo patrón ISP/DIP que {@link TaskService}.
 */
public interface TrainingService {

    /** Crea una nueva capacitación y notifica al colaborador asignado. */
    TrainingResponse create(CreateTrainingRequest req, String createdBy);

    /** Retorna las capacitaciones activas asignadas al usuario dado. */
    List<TrainingResponse> getMyTrainings(String userId);

    /** Retorna todas las capacitaciones activas de una sucursal (gerencial). */
    List<TrainingResponse> getByStore(String storeId);

    /** Retorna una capacitación por ID. */
    TrainingResponse getById(String id);

    /** Actualiza el progreso/estado de una capacitación. */
    TrainingResponse updateProgress(String id, UpdateTrainingProgressRequest req, String currentUser);

    /** Retorna TODAS las capacitaciones activas de todas las sucursales (solo ADMIN). */
    List<TrainingResponse> getAll();

    /** Soft-delete: marca activo = false. */
    void deactivate(String id);

    // ── Variantes paginadas ───────────────────────────────────────────────
    Page<TrainingResponse> getMyTrainingsPaged(String userId, int page, int size);
    Page<TrainingResponse> getByStorePaged(String storeId, int page, int size);
    Page<TrainingResponse> getAllPaged(int page, int size);

    /**
     * Crea una capacitación usando una plantilla como base.
     * Copia metadata + materiales de la plantilla. El creador puede
     * sobreescribir assignedUserId, storeId, shift y dueAt.
     */
    TrainingResponse createFromTemplate(String templateId, String assignedUserId,
                                        String storeId, String shift, Instant dueAt,
                                        String createdBy);

    /** Marca un material como visto por el ejecutador asignado. */
    TrainingResponse markMaterialViewed(String trainingId, String materialId, String currentUser);
}
