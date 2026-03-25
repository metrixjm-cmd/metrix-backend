package com.metrix.api.repository;

import com.metrix.api.model.TaskEvidence;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskEvidenceRepository extends MongoRepository<TaskEvidence, String> {

    List<TaskEvidence> findByTaskId(String taskId);

    List<TaskEvidence> findByTaskIdIn(java.util.Collection<String> taskIds);
}
