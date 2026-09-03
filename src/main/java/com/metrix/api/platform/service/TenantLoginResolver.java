package com.metrix.api.platform.service;

import com.metrix.api.platform.model.PlatformUser;
import com.metrix.api.platform.model.TenantAdminIndex;
import com.metrix.api.platform.repository.PlatformUserRepository;
import com.metrix.api.platform.repository.TenantAdminIndexRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.mongodb.ConnectionString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantLoginResolver {

    private final PlatformUserRepository platformUserRepository;
    private final TenantAdminIndexRepository tenantAdminIndexRepository;
    private final UserRepository userRepository;

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    public LoginResolution resolve(String numeroUsuario) {
        Optional<PlatformUser> platformUser = platformUserRepository.findByNumeroUsuario(numeroUsuario);
        if (platformUser.isPresent() && platformUser.get().isActivo()) {
            return LoginResolution.platform(platformUser.get());
        }

        Optional<TenantAdminIndex> tenantUser = tenantAdminIndexRepository.findByNumeroUsuario(numeroUsuario);
        if (tenantUser.isPresent()) {
            return LoginResolution.tenant(
                    tenantUser.get().getDatabaseName(),
                    tenantUser.get().getInstanceId(),
                    tenantUser.get().getEmpresaNombre()
            );
        }

        // Compatibilidad: usuarios del tenant por defecto (desarrollo / instancia legacy)
        if (userRepository.findByNumeroUsuario(numeroUsuario).isPresent()) {
            return LoginResolution.legacy(defaultDatabaseName());
        }

        return LoginResolution.notFound();
    }

    private String defaultDatabaseName() {
        return new ConnectionString(mongoUri).getDatabase();
    }

    public enum LoginType {
        PLATFORM, TENANT, LEGACY, NOT_FOUND
    }

    public record LoginResolution(
            LoginType type,
            PlatformUser platformUser,
            String databaseName,
            String instanceId,
            String empresaNombre
    ) {
        static LoginResolution platform(PlatformUser user) {
            return new LoginResolution(LoginType.PLATFORM, user, null, null, null);
        }

        static LoginResolution tenant(String databaseName, String instanceId, String empresaNombre) {
            return new LoginResolution(LoginType.TENANT, null, databaseName, instanceId, empresaNombre);
        }

        static LoginResolution legacy(String databaseName) {
            return new LoginResolution(LoginType.LEGACY, null, databaseName, null, null);
        }

        static LoginResolution notFound() {
            return new LoginResolution(LoginType.NOT_FOUND, null, null, null, null);
        }
    }
}
