package com.metrix.api.repository;

import com.metrix.api.model.TrainingLevel;
import com.metrix.api.model.TrainingTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface TrainingTemplateRepository extends MongoRepository<TrainingTemplate, String> {

    Page<TrainingTemplate> findByActivoTrue(Pageable pageable);

    Page<TrainingTemplate> findByCategoryAndActivoTrue(String category, Pageable pageable);

    Page<TrainingTemplate> findByLevelAndActivoTrue(TrainingLevel level, Pageable pageable);

    Page<TrainingTemplate> findByCategoryAndLevelAndActivoTrue(String category, TrainingLevel level, Pageable pageable);

    Page<TrainingTemplate> findByTagsContainingAndActivoTrue(String tag, Pageable pageable);

    /** Para el selector de "crear training desde plantilla" — solo título e id. */
    @Query(value = "{ 'activo': true }", fields = "{ 'title': 1, 'description': 1, 'category': 1, 'level': 1, 'times_used': 1 }")
    List<TrainingTemplate> findSummaries();

    long countByActivoTrue();
}
