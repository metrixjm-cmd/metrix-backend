package com.metrix.api.service;

import com.metrix.api.dto.CreateTrainingRequest;
import com.metrix.api.dto.TrainingResponse;
import com.metrix.api.dto.UpdateTrainingProgressRequest;

import java.util.List;

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

    /** Soft-delete: marca activo = false. */
    void deactivate(String id);
}
