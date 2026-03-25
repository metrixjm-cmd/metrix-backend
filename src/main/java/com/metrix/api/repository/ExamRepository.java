package com.metrix.api.repository;

import com.metrix.api.model.Exam;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ExamRepository extends MongoRepository<Exam, String> {

    List<Exam> findByStoreIdAndActivoTrue(String storeId);

    List<Exam> findByTrainingIdAndActivoTrue(String trainingId);
}
