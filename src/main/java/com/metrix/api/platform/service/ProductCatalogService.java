package com.metrix.api.platform.service;

import com.metrix.api.dto.LicensePackageResponse;
import com.metrix.api.model.LicensePackage;
import com.metrix.api.platform.repository.LicensePackageRepository;
import com.metrix.api.service.LicensePackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private final LicensePackageRepository licensePackageRepository;
    private final LicensePackageService licensePackageService;

    public List<LicensePackageResponse> getActiveCatalog() {
        return licensePackageRepository.findByActivoTrueOrderByIdAsc().stream()
                .map(this::toPublicResponse)
                .toList();
    }

    public LicensePackageResponse getActivePackage(String id) {
        LicensePackage pkg = licensePackageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Paquete no encontrado"));
        if (!pkg.isActivo()) {
            throw new IllegalStateException("Paquete no disponible");
        }
        return licensePackageService.getById(id);
    }

    private LicensePackageResponse toPublicResponse(LicensePackage entity) {
        return licensePackageService.getById(entity.getId());
    }
}
