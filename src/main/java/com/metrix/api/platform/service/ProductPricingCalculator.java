package com.metrix.api.platform.service;

import com.metrix.api.model.LicensePackage;
import com.metrix.api.model.LicensePricingModel;
import com.metrix.api.platform.repository.LicensePackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductPricingCalculator {

    private final LicensePackageRepository licensePackageRepository;

    public PricingBreakdown calculate(String packageId, int sucursalesContratadas) {
        LicensePackage pkg = licensePackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Paquete no encontrado: " + packageId));

        if (!pkg.isActivo()) {
            throw new IllegalStateException("El paquete seleccionado no está disponible.");
        }

        int sucursales = Math.max(1, sucursalesContratadas);
        validateSucursales(pkg, sucursales);

        BigDecimal mensual = pkg.getPrecioMensual() != null ? pkg.getPrecioMensual() : BigDecimal.ZERO;
        BigDecimal implementacion = pkg.getPrecioImplementacion() != null
                ? pkg.getPrecioImplementacion() : BigDecimal.ZERO;

        BigDecimal subtotalMensual = switch (pkg.getPricingModel()) {
            case PER_BRANCH -> mensual.multiply(BigDecimal.valueOf(sucursales));
            case FLAT_MONTHLY, PER_USER -> mensual;
        };

        BigDecimal totalCobrado = subtotalMensual.add(implementacion);

        return new PricingBreakdown(
                subtotalMensual,
                implementacion,
                totalCobrado,
                pkg.getMoneda() != null ? pkg.getMoneda() : "MXN"
        );
    }

    private void validateSucursales(LicensePackage pkg, int sucursales) {
        if (pkg.getMinSucursales() != null && sucursales < pkg.getMinSucursales()) {
            throw new IllegalArgumentException(
                    "El plan requiere al menos " + pkg.getMinSucursales() + " sucursal(es).");
        }
        if (pkg.getMaxSucursales() != null && sucursales > pkg.getMaxSucursales()) {
            throw new IllegalArgumentException(
                    "El plan admite como máximo " + pkg.getMaxSucursales() + " sucursal(es).");
        }
    }

    public record PricingBreakdown(
            BigDecimal subtotalMensual,
            BigDecimal cargoImplementacion,
            BigDecimal totalCobrado,
            String moneda
    ) {
    }
}
