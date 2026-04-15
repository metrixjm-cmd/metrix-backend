package com.metrix.api.repository;

import com.metrix.api.model.TaskTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskTemplateRepository extends MongoRepository<TaskTemplate, String> {

    /** Todas las plantillas activas, ordenadas por título ascendente. */
    List<TaskTemplate> findByActivoTrueOrderByTitleAsc();

    /**
     * Búsqueda case-insensitive por título (substring match).
     * Se limita a las primeras 20 coincidencias para no saturar el dropdown.
     */
    @Query("{ 'activo': true, 'title': { $regex: ?0, $options: 'i' } }")
    List<TaskTemplate> findTop20ByTitleContainingIgnoreCaseAndActivoTrue(String query);

    /** Verificar si ya existe una plantilla con ese título (evitar duplicados). */
    boolean existsByTitleIgnoreCaseAndActivoTrue(String title);
}
