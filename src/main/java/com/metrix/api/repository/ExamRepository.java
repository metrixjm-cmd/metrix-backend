package com.metrix.api.repository;

import com.metrix.api.model.Exam;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface ExamRepository extends MongoRepository<Exam, String> {

    List<Exam> findByStoreIdAndActivoTrue(String storeId);

    /** Exámenes de la sucursal + catálogo global (store_id ausente o null). */
    @Query("{ 'activo': true, $or: [ { 'store_id': ?0 }, { 'store_id': null }, { 'store_id': '' } ] }")
    List<Exam> findAvailableForStore(String storeId);

    List<Exam> findByActivoTrue();

    List<Exam> findByTrainingIdAndActivoTrue(String trainingId);
}
