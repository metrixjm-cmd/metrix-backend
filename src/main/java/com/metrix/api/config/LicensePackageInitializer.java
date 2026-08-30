package com.metrix.api.config;

import com.metrix.api.platform.repository.LicensePackageRepository;
import com.metrix.api.service.LicensePackageSeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LicensePackageInitializer {

    private final LicensePackageRepository repository;

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (repository.count() > 0) {
            return;
        }
        repository.saveAll(LicensePackageSeed.defaults());
        log.info("[LicensePackage] Catálogo inicial cargado (4 paquetes)");
    }
}
