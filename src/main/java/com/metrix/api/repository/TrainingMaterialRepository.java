package com.metrix.api.repository;

import com.metrix.api.model.MaterialType;
import com.metrix.api.model.TrainingMaterial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface TrainingMaterialRepository extends MongoRepository<TrainingMaterial, String> {

    // ── Listados paginados ─────────────────────────────────────────────────

    Page<TrainingMaterial> findByActivoTrue(Pageable pageable);

    Page<TrainingMaterial> findByTypeAndActivoTrue(MaterialType type, Pageable pageable);

    Page<TrainingMaterial> findByCategoryAndActivoTrue(String category, Pageable pageable);

    Page<TrainingMaterial> findByTypeAndCategoryAndActivoTrue(MaterialType type, String category, Pageable pageable);

    /** Materiales globales (storeId null) + materiales de una sucursal específica. */
    @Query("{ 'activo': true, '$or': [ { 'store_id': null }, { 'store_id': ?0 } ] }")
    Page<TrainingMaterial> findVisibleForStore(String storeId, Pageable pageable);

    // ── Búsqueda por tag ───────────────────────────────────────────────────

    Page<TrainingMaterial> findByTagsContainingAndActivoTrue(String tag, Pageable pageable);

    // ── Tags disponibles (para autocomplete) ──────────────────────────────

    @Query(value = "{ 'activo': true }", fields = "{ 'tags': 1 }")
    List<TrainingMaterial> findAllTags();

    // ── Contadores ─────────────────────────────────────────────────────────

    long countByActivoTrue();

    long countByTypeAndActivoTrue(MaterialType type);
}
