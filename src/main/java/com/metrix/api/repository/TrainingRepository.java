package com.metrix.api.repository;

import com.metrix.api.model.Training;
import com.metrix.api.model.TrainingStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos para {@link Training} (Sprint 10).
 * <p>
 * Notación doble guion bajo para sub-documentos MongoDB:
 * {@code Progress_Status} → consulta {@code progress.status} en el documento.
 */
@Repository
public interface TrainingRepository extends MongoRepository<Training, String> {

    // ── Portfolio de usuario ─────────────────────────────────────────────

    List<Training> findByAssignedUserIdAndActivoTrue(String assignedUserId);

    List<Training> findByAssignedUserIdAndProgress_StatusAndActivoTrue(
            String userId, TrainingStatus status);

    // ── Vista gerencial / admin ──────────────────────────────────────────

    List<Training> findByStoreIdAndActivoTrue(String storeId);

    List<Training> findByStoreIdAndProgress_StatusAndActivoTrue(
            String storeId, TrainingStatus status);

    // ── Conteos para KPI de Completación ────────────────────────────────

    long countByStoreIdAndActivoTrue(String storeId);

    long countByStoreIdAndProgress_StatusAndActivoTrue(String storeId, TrainingStatus status);
}
