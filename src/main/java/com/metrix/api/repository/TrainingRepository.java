package com.metrix.api.repository;

import com.metrix.api.model.Training;
import com.metrix.api.model.TrainingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    List<Training> findByStoreIdAndCreatedByInAndActivoTrue(
            String storeId, Collection<String> createdBy);

    List<Training> findByStoreIdAndProgress_StatusAndActivoTrue(
            String storeId, TrainingStatus status);

    List<Training> findByAssignmentGroupIdAndActivoTrue(String assignmentGroupId);

    // ── Vista global ADMIN ───────────────────────────────────────────────

    /** Todas las capacitaciones activas en todas las sucursales (uso exclusivo ADMIN). */
    List<Training> findByActivoTrue();

    // ── Versiones paginadas ──────────────────────────────────────────────

    Page<Training> findByAssignedUserIdAndActivoTrue(String assignedUserId, Pageable pageable);

    Page<Training> findByStoreIdAndActivoTrue(String storeId, Pageable pageable);

    Page<Training> findByStoreIdAndCreatedByInAndActivoTrue(
            String storeId, Collection<String> createdBy, Pageable pageable);

    Page<Training> findByActivoTrue(Pageable pageable);

    // ── Conteos para KPI de Completación ────────────────────────────────

    long countByStoreIdAndActivoTrue(String storeId);

    long countByStoreIdAndProgress_StatusAndActivoTrue(String storeId, TrainingStatus status);
}
