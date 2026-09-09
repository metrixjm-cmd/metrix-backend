package com.metrix.api.platform.repository;

import com.metrix.api.platform.model.TenantAdminIndex;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TenantAdminIndexRepository extends MongoRepository<TenantAdminIndex, String> {

    Optional<TenantAdminIndex> findByNumeroUsuario(String numeroUsuario);

    List<TenantAdminIndex> findAllByNumeroUsuario(String numeroUsuario);

    long countByNumeroUsuario(String numeroUsuario);

    Optional<TenantAdminIndex> findByCodigoEmpresaAndNumeroUsuario(String codigoEmpresa, String numeroUsuario);

    boolean existsByNumeroUsuario(String numeroUsuario);

    boolean existsByCodigoEmpresaAndNumeroUsuario(String codigoEmpresa, String numeroUsuario);

    List<TenantAdminIndex> findByInstanceId(String instanceId);
}
