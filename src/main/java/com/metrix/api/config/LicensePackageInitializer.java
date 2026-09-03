package com.metrix.api.config;

import com.metrix.api.model.LicensePackage;
import com.metrix.api.platform.license.LicenseFeatureCodes;
import com.metrix.api.platform.repository.LicensePackageRepository;
import com.metrix.api.service.LicensePackageSeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LicensePackageInitializer {

    private final LicensePackageRepository repository;

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (repository.count() == 0) {
            repository.saveAll(LicensePackageSeed.defaults());
            log.info("[LicensePackage] Catálogo inicial cargado (4 paquetes)");
            return;
        }
        syncFeatureCodes();
    }

    /** Rellena featureCodes en paquetes sembrados antes de este campo. */
    private void syncFeatureCodes() {
        Map<String, LicensePackage> seeds = LicensePackageSeed.defaults().stream()
                .collect(Collectors.toMap(LicensePackage::getId, Function.identity()));
        int updated = 0;
        for (LicensePackage existing : repository.findAll()) {
            if (existing.getFeatureCodes() != null && !existing.getFeatureCodes().isEmpty()) {
                continue;
            }
            LicensePackage seed = seeds.get(existing.getId());
            List<String> codes = seed != null && seed.getFeatureCodes() != null
                    ? seed.getFeatureCodes()
                    : LicenseFeatureCodes.defaultsForPackageId(existing.getId());
            existing.setFeatureCodes(codes);
            repository.save(existing);
            updated++;
        }
        if (updated > 0) {
            log.info("[LicensePackage] featureCodes sincronizados en {} paquetes", updated);
        }
    }
}
