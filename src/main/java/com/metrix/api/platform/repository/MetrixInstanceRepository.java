package com.metrix.api.platform.repository;

import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.MetrixInstanceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MetrixInstanceRepository extends MongoRepository<MetrixInstance, String> {

    List<MetrixInstance> findAllByOrderByCreatedAtDesc();

    List<MetrixInstance> findByStatusOrderByCreatedAtDesc(MetrixInstanceStatus status);
}
