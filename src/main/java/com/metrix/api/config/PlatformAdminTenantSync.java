package com.metrix.api.config;

import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.TenantDatabaseNames;
import com.metrix.api.platform.model.PlatformUser;
import com.metrix.api.platform.repository.PlatformUserRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Admin 0 vive en {@code metrix_platform} para login, pero la app operativa
 * (incidencias, tareas, KPIs) lee {@code users} de la BD operativa por defecto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminTenantSync {

    private final PlatformUserRepository platformUserRepository;
    private final UserRepository userRepository;
    private final TenantDatabaseNames tenantDatabaseNames;

    @EventListener(ApplicationReadyEvent.class)
    public void syncPlatformAdminsToOperationalDb() {
        for (PlatformUser platformUser : platformUserRepository.findAll()) {
            if (!platformUser.isPlatformAdmin() || !platformUser.isActivo()) {
                continue;
            }
            syncOne(platformUser);
        }
    }

    private void syncOne(PlatformUser platformUser) {
        String previousDb = TenantContext.getDatabaseName();
        boolean previousPlatform = TenantContext.isPlatformAdmin();
        try {
            TenantContext.setPlatformAdmin(false);
            TenantContext.setDatabaseName(tenantDatabaseNames.getDefaultOperationalDatabase());

            userRepository.findByNumeroUsuario(platformUser.getNumeroUsuario())
                    .ifPresentOrElse(existing -> {
                        if (!existing.isActivo()) {
                            existing.setActivo(true);
                            userRepository.save(existing);
                            log.info("[Platform] Usuario operativo reactivado: {}",
                                    platformUser.getNumeroUsuario());
                        }
                    }, () -> {
                        User mirror = User.builder()
                                .numeroUsuario(platformUser.getNumeroUsuario())
                                .nombre(platformUser.getNombre())
                                .puesto("Administrador Plataforma")
                                .password(platformUser.getPassword())
                                .roles(platformUser.getRoles() != null
                                        ? platformUser.getRoles() : Set.of(Role.ADMIN))
                                .activo(true)
                                .build();
                        userRepository.save(mirror);
                        log.info("[Platform] Usuario operativo espejo creado: {}",
                                platformUser.getNumeroUsuario());
                    });
        } finally {
            TenantContext.setDatabaseName(previousDb);
            TenantContext.setPlatformAdmin(previousPlatform);
        }
    }
}
