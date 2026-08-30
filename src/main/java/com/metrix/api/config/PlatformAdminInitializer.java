package com.metrix.api.config;

import com.metrix.api.model.Role;
import com.metrix.api.platform.model.PlatformUser;
import com.metrix.api.platform.repository.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Admin 0 (super-administrador de la plataforma METRIX).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminInitializer {

    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${metrix.platform.admin.numero-usuario:ADMIN001}")
    private String adminNumeroUsuario;

    @Value("${metrix.platform.admin.password:Admin123456}")
    private String adminPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void seedAdminZero() {
        if (platformUserRepository.existsByNumeroUsuario(adminNumeroUsuario)) {
            return;
        }
        platformUserRepository.save(PlatformUser.builder()
                .numeroUsuario(adminNumeroUsuario)
                .nombre("Administrador Plataforma METRIX")
                .password(passwordEncoder.encode(adminPassword))
                .roles(Set.of(Role.ADMIN))
                .platformAdmin(true)
                .activo(true)
                .build());
        log.info("[Platform] Admin 0 creado: {}", adminNumeroUsuario);
    }
}
