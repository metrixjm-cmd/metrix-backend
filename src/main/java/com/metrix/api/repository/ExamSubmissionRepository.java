package com.metrix.api.repository;

import com.metrix.api.model.ExamSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ExamSubmissionRepository extends MongoRepository<ExamSubmission, String> {

    List<ExamSubmission> findByExamIdOrderBySubmittedAtDesc(String examId);

    List<ExamSubmission> findByUserIdOrderBySubmittedAtDesc(String userId);

    long countByExamId(String examId);

    long countByExamIdAndPassedTrue(String examId);

    long countByExamIdAndUserId(String examId, String userId);
}
