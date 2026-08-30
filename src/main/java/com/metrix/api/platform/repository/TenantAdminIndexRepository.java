package com.metrix.api.platform.repository;

import com.metrix.api.platform.model.TenantAdminIndex;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TenantAdminIndexRepository extends MongoRepository<TenantAdminIndex, String> {

    Optional<TenantAdminIndex> findByNumeroUsuario(String numeroUsuario);

    boolean existsByNumeroUsuario(String numeroUsuario);
}
