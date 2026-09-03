package com.metrix.api.platform.service;

import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.TenantDatabaseNames;
import com.metrix.api.platform.model.TenantAdminIndex;
import com.metrix.api.platform.repository.PlatformUserRepository;
import com.metrix.api.platform.repository.TenantAdminIndexRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Índice global {@code numeroUsuario} → BD del tenant.
 * Sin esta fila, el login solo encuentra al ADMIN de provision.
 */
@Service
@RequiredArgsConstructor
public class TenantUserIndexService {

    private final TenantAdminIndexRepository tenantAdminIndexRepository;
    private final PlatformUserRepository platformUserRepository;
    private final UserRepository userRepository;
    private final TenantDatabaseNames tenantDatabaseNames;

    public boolean isTaken(String numeroUsuario) {
        if (numeroUsuario == null || numeroUsuario.isBlank()) {
            return false;
        }
        return platformUserRepository.existsByNumeroUsuario(numeroUsuario)
                || tenantAdminIndexRepository.existsByNumeroUsuario(numeroUsuario)
                || userRepository.existsByNumeroUsuario(numeroUsuario);
    }

    public void assertNumeroUsuarioAvailable(String numeroUsuario) {
        if (isTaken(numeroUsuario)) {
            throw new IllegalArgumentException("El #Usuario ya está en uso. Elige otro.");
        }
    }

    public void index(String numeroUsuario, String databaseName, String instanceId, String empresaNombre) {
        if (numeroUsuario == null || numeroUsuario.isBlank()) {
            return;
        }
        if (tenantAdminIndexRepository.existsByNumeroUsuario(numeroUsuario)) {
            return;
        }
        tenantAdminIndexRepository.save(TenantAdminIndex.builder()
                .numeroUsuario(numeroUsuario)
                .databaseName(resolveDatabaseName(databaseName))
                .instanceId(blankToNull(instanceId))
                .empresaNombre(blankToNull(empresaNombre))
                .build());
    }

    /** Indexa al usuario en la BD/instancia del {@link TenantContext} actual. */
    public void indexCurrentTenantUser(String numeroUsuario) {
        index(numeroUsuario, TenantContext.getDatabaseName(), TenantContext.getInstanceId(), null);
    }

    public void remove(String numeroUsuario) {
        if (numeroUsuario == null || numeroUsuario.isBlank()) {
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
