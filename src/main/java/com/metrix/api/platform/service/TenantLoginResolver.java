package com.metrix.api.platform.service;

import com.metrix.api.platform.EmpresaCodigos;
import com.metrix.api.platform.model.PlatformUser;
import com.metrix.api.platform.model.TenantAdminIndex;
import com.metrix.api.platform.repository.PlatformUserRepository;
import com.metrix.api.platform.repository.TenantAdminIndexRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.mongodb.ConnectionString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantLoginResolver {

    private final PlatformUserRepository platformUserRepository;
    private final TenantAdminIndexRepository tenantAdminIndexRepository;
    private final UserRepository userRepository;

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    public LoginResolution resolve(String codigoEmpresa, String numeroUsuario) {
        if (numeroUsuario == null || numeroUsuario.isBlank()) {
            return LoginResolution.notFound();
        }
        String code = EmpresaCodigos.normalize(codigoEmpresa);
        if (!EmpresaCodigos.isValidFormat(code)) {
            return LoginResolution.notFound();
        }

        if (EmpresaCodigos.isPlatform(code)) {
            return resolvePlatform(numeroUsuario);
        }
        if (EmpresaCodigos.isBlank(code)) {
            return resolveUnspecified(numeroUsuario);
        }
        return resolveTenantCode(code, numeroUsuario);
    }

    private LoginResolution resolvePlatform(String numeroUsuario) {
        Optional<PlatformUser> platformUser = platformUserRepository.findByNumeroUsuario(numeroUsuario);
        if (platformUser.isPresent() && platformUser.get().isActivo()) {
            return LoginResolution.platform(platformUser.get());
        }
        return LoginResolution.notFound();
    }

    /**
     * Sin código: Admin 0 si el #Usuario es de plataforma; si el índice tiene
     * exactamente una fila, ese tenant (migración); si no, users de metrix_db.
     */
    private LoginResolution resolveUnspecified(String numeroUsuario) {
        Optional<PlatformUser> platformUser = platformUserRepository.findByNumeroUsuario(numeroUsuario);
        if (platformUser.isPresent() && platformUser.get().isActivo()) {
            return LoginResolution.platform(platformUser.get());
        }

        List<TenantAdminIndex> matches = tenantAdminIndexRepository.findAllByNumeroUsuario(numeroUsuario);
        if (matches.size() == 1) {
            TenantAdminIndex row = matches.get(0);
            return LoginResolution.tenant(
                    row.getDatabaseName(),
                    row.getInstanceId(),
                    row.getEmpresaNombre(),
                    row.getCodigoEmpresa());
        }

        if (userRepository.findByNumeroUsuario(numeroUsuario).isPresent()) {
            return LoginResolution.legacy(defaultDatabaseName());
        }

        return LoginResolution.notFound();
    }

    private LoginResolution resolveTenantCode(String codigoEmpresa, String numeroUsuario) {
        Optional<TenantAdminIndex> tenantUser =
                tenantAdminIndexRepository.findByCodigoEmpresaAndNumeroUsuario(codigoEmpresa, numeroUsuario);
        if (tenantUser.isPresent()) {
            TenantAdminIndex row = tenantUser.get();
            return LoginResolution.tenant(
                    row.getDatabaseName(),
                    row.getInstanceId(),
                    row.getEmpresaNombre(),
                    codigoEmpresa);
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
            String empresaNombre,
            String codigoEmpresa
    ) {
        public static LoginResolution platform(PlatformUser user) {
            return new LoginResolution(LoginType.PLATFORM, user, null, null, null, EmpresaCodigos.PLATFORM);
        }

        public static LoginResolution tenant(String databaseName, String instanceId,
                                      String empresaNombre, String codigoEmpresa) {
            return new LoginResolution(LoginType.TENANT, null, databaseName, instanceId,
                    empresaNombre, codigoEmpresa);
        }

        public static LoginResolution legacy(String databaseName) {
            return new LoginResolution(LoginType.LEGACY, null, databaseName, null, null, null);
        }

        public static LoginResolution notFound() {
            return new LoginResolution(LoginType.NOT_FOUND, null, null, null, null, null);
        }
    }
}
