package com.metrix.api.repository;

import com.metrix.api.model.ExamTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface ExamTemplateRepository extends MongoRepository<ExamTemplate, String> {

    Page<ExamTemplate> findByActivoTrue(Pageable pageable);

    Page<ExamTemplate> findByCategoryAndActivoTrue(String category, Pageable pageable);

    Page<ExamTemplate> findByTagsContainingAndActivoTrue(String tag, Pageable pageable);

    @Query(value = "{ 'activo': true, '$or': [ { 'store_id': null }, { 'store_id': ?0 } ] }")
    Page<ExamTemplate> findVisibleForStore(String storeId, Pageable pageable);

    /** Listado resumido para selector — solo campos esenciales. */
    @Query(value = "{ 'activo': true }", fields = "{ 'title': 1, 'description': 1, 'category': 1, 'passing_score': 1, 'times_used': 1, 'questions': 1 }")
    List<ExamTemplate> findSummaries();

    long countByActivoTrue();
}
