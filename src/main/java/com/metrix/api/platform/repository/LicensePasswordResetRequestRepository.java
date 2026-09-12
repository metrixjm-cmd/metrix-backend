package com.metrix.api.platform.repository;

import com.metrix.api.platform.model.LicensePasswordResetRequest;
import com.metrix.api.platform.model.LicensePasswordResetStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LicensePasswordResetRequestRepository
        extends MongoRepository<LicensePasswordResetRequest, String> {

    Optional<LicensePasswordResetRequest> findFirstByCodigoEmpresaAndNumeroUsuarioAndStatus(
            String codigoEmpresa, String numeroUsuario, LicensePasswordResetStatus status);

    List<LicensePasswordResetRequest> findByCodigoEmpresaAndNumeroUsuarioAndStatus(
            String codigoEmpresa, String numeroUsuario, LicensePasswordResetStatus status);

    Optional<LicensePasswordResetRequest> findByTokenHash(String tokenHash);

    List<LicensePasswordResetRequest> findAllByOrderByRequestedAtDesc();

    List<LicensePasswordResetRequest> findByInstanceIdOrderByRequestedAtDesc(String instanceId);
}
