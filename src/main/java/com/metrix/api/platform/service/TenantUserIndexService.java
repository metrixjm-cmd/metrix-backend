package com.metrix.api.platform.service;

import com.metrix.api.platform.EmpresaCodigos;
import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.TenantDatabaseNames;
import com.metrix.api.platform.model.TenantAdminIndex;
import com.metrix.api.platform.repository.TenantAdminIndexRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Índice de login {@code codigoEmpresa + numeroUsuario} → BD del tenant.
 * El #Usuario se puede repetir entre restaurantes; no entre usuarios del mismo tenant.
 */
@Service
@RequiredArgsConstructor
public class TenantUserIndexService {

    private final TenantAdminIndexRepository tenantAdminIndexRepository;
    private final UserRepository userRepository;
    private final TenantDatabaseNames tenantDatabaseNames;

    public boolean isTaken(String numeroUsuario) {
        return isTaken(numeroUsuario, TenantContext.getCodigoEmpresa());
    }

    public boolean isTaken(String numeroUsuario, String codigoEmpresa) {
        if (numeroUsuario == null || numeroUsuario.isBlank()) {
            return false;
        }
        String code = EmpresaCodigos.normalize(codigoEmpresa);
        if (!code.isEmpty() && !EmpresaCodigos.isPlatform(code)
                && tenantAdminIndexRepository.existsByCodigoEmpresaAndNumeroUsuario(code, numeroUsuario)) {
            return true;
        }
        return userRepository.existsByNumeroUsuario(numeroUsuario);
    }

    public void assertNumeroUsuarioAvailable(String numeroUsuario) {
        assertNumeroUsuarioAvailable(numeroUsuario, TenantContext.getCodigoEmpresa());
    }

    public void assertNumeroUsuarioAvailable(String numeroUsuario, String codigoEmpresa) {
        if (isTaken(numeroUsuario, codigoEmpresa)) {
            throw new IllegalArgumentException("El #Usuario ya está en uso. Elige otro.");
        }
    }

    public void index(String numeroUsuario, String databaseName, String instanceId,
                       String empresaNombre, String codigoEmpresa) {
        if (numeroUsuario == null || numeroUsuario.isBlank()) {
            return;
        }
        String code = EmpresaCodigos.normalize(codigoEmpresa);
        if (!code.isEmpty()
                && tenantAdminIndexRepository.existsByCodigoEmpresaAndNumeroUsuario(code, numeroUsuario)) {
            return;
        }
        if (code.isEmpty() && tenantAdminIndexRepository.existsByNumeroUsuario(numeroUsuario)) {
            return;
        }
        tenantAdminIndexRepository.save(TenantAdminIndex.builder()
                .numeroUsuario(numeroUsuario)
                .codigoEmpresa(blankToNull(code))
                .databaseName(resolveDatabaseName(databaseName))
                .instanceId(blankToNull(instanceId))
                .empresaNombre(blankToNull(empresaNombre))
                .build());
    }

    /** Indexa al usuario en la BD/instancia del {@link TenantContext} actual. */
    public void indexCurrentTenantUser(String numeroUsuario) {
        index(numeroUsuario, TenantContext.getDatabaseName(), TenantContext.getInstanceId(),
                null, TenantContext.getCodigoEmpresa());
    }

    public void remove(String numeroUsuario) {
        if (numeroUsuario == null || numeroUsuario.isBlank()) {
            return;
        }
        String code = EmpresaCodigos.normalize(TenantContext.getCodigoEmpresa());
        if (!code.isEmpty()) {
            tenantAdminIndexRepository.findByCodigoEmpresaAndNumeroUsuario(code, numeroUsuario)
                    .ifPresent(tenantAdminIndexRepository::delete);
            return;
        }
        tenantAdminIndexRepository.findByNumeroUsuario(numeroUsuario)
                .ifPresent(tenantAdminIndexRepository::delete);
    }

    private String resolveDatabaseName(String databaseName) {
        if (databaseName != null && !databaseName.isBlank()
                && !databaseName.equals(tenantDatabaseNames.getPlatformDatabase())) {
            return databaseName;
        }
        return tenantDatabaseNames.getDefaultOperationalDatabase();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
