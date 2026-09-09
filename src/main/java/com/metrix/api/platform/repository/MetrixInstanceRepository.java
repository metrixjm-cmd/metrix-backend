package com.metrix.api.platform.repository;

import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.MetrixInstanceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MetrixInstanceRepository extends MongoRepository<MetrixInstance, String> {

    Optional<MetrixInstance> findByCodigoEmpresa(String codigoEmpresa);

    boolean existsByCodigoEmpresa(String codigoEmpresa);

    List<MetrixInstance> findAllByOrderByCreatedAtDesc();

    List<MetrixInstance> findByStatusOrderByCreatedAtDesc(MetrixInstanceStatus status);

    List<MetrixInstance> findByOnTrialTrueAndStatusAndTrialEndsAtBefore(
            MetrixInstanceStatus status, Instant cutoff);
}
