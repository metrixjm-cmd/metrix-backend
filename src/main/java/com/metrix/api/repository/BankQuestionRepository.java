package com.metrix.api.repository;

import com.metrix.api.model.BankQuestion;
import com.metrix.api.model.QuestionDifficulty;
import com.metrix.api.model.QuestionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface BankQuestionRepository extends MongoRepository<BankQuestion, String> {

    // ── Listados paginados ─────────────────────────────────────────────────

    Page<BankQuestion> findByActivoTrue(Pageable pageable);

    Page<BankQuestion> findByTypeAndActivoTrue(QuestionType type, Pageable pageable);

    Page<BankQuestion> findByCategoryAndActivoTrue(String category, Pageable pageable);

    Page<BankQuestion> findByTypeAndCategoryAndActivoTrue(QuestionType type, String category, Pageable pageable);

    Page<BankQuestion> findByDifficultyAndActivoTrue(QuestionDifficulty difficulty, Pageable pageable);

    Page<BankQuestion> findByTagsContainingAndActivoTrue(String tag, Pageable pageable);

    /** Globales (storeId null) + preguntas de la sucursal específica. */
    @Query("{ 'activo': true, '$or': [ { 'store_id': null }, { 'store_id': ?0 } ] }")
    Page<BankQuestion> findVisibleForStore(String storeId, Pageable pageable);

    // ── Tags disponibles ───────────────────────────────────────────────────

    @Query(value = "{ 'activo': true }", fields = "{ 'tags': 1 }")
    List<BankQuestion> findAllForTags();

    // ── Contadores ─────────────────────────────────────────────────────────

    long countByActivoTrue();

    long countByTypeAndActivoTrue(QuestionType type);
}
