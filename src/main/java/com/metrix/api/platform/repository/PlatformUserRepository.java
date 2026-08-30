package com.metrix.api.platform.repository;

import com.metrix.api.platform.model.PlatformUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PlatformUserRepository extends MongoRepository<PlatformUser, String> {

    Optional<PlatformUser> findByNumeroUsuario(String numeroUsuario);

    boolean existsByNumeroUsuario(String numeroUsuario);
}
