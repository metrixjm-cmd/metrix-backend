package com.metrix.api.platform.service;

import com.metrix.api.model.LicensePricingModel;
import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.license.LicenseFeatureCodes;
import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.ProductOrder;
import com.metrix.api.platform.model.ProductOrderPackageSnapshot;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import com.metrix.api.platform.repository.ProductOrderRepository;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cupos y módulos del paquete contratado (snapshot de la orden).
 * <p>
 * Sin {@code instanceId} (demo legacy / Admin 0) no aplica restricciones.
 */
@Service
@RequiredArgsConstructor
public class TenantLicenseGuard {

    private final MetrixInstanceRepository metrixInstanceRepository;
    private final ProductOrderRepository productOrderRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;

    public void assertCanCreateUser() {
        ProductOrder order = resolveOrderOrNull();
        if (order == null) {
            return;
        }
        Integer maxUsuarios = snapshotMaxUsuarios(order);
        if (maxUsuarios == null || maxUsuarios <= 0) {
            return;
        }
        long current = userRepository.countByActivoTrue();
        if (current >= maxUsuarios) {
            throw new IllegalStateException(
                    "Límite de usuarios del plan alcanzado (" + maxUsuarios
                            + "). Actualiza tu licencia o desactiva colaboradores.");
        }
    }

    public void assertCanCreateStore() {
        ProductOrder order = resolveOrderOrNull();
        if (order == null) {
            return;
        }
        int maxSucursales = resolveMaxSucursales(order);
        if (maxSucursales <= 0) {
            return;
        }
        long current = storeRepository.countByActivoTrue();
        if (current >= maxSucursales) {
            throw new IllegalStateException(
                    "Límite de sucursales del plan alcanzado (" + maxSucursales
                            + "). Actualiza tu licencia o desactiva sucursales.");
        }
    }

    /**
     * Features del tenant actual para hidratar el login.
     * {@code null} = sin restricción (legacy / Admin 0).
     */
    public List<String> resolveLicensedFeaturesOrUnrestricted() {
        if (TenantContext.isPlatformAdmin()) {
            return null;
        }
        String instanceId = TenantContext.getInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }
        ProductOrder order = resolveOrderOrNull();
        if (order == null) {
            return List.of();
        }
        return resolveFeatureCodes(order);
    }

    public void assertFeature(String featureCode) {
        List<String> features = resolveLicensedFeaturesOrUnrestricted();
        if (features == null) {
            return;
        }
        if (!LicenseFeatureCodes.includes(features, featureCode)) {
            throw new AccessDeniedException(
                    "Tu plan no incluye el módulo " + featureCode + ". Actualiza tu licencia.");
        }
    }

    public boolean isFeatureEnforcementActive() {
        return resolveLicensedFeaturesOrUnrestricted() != null;
    }

    private ProductOrder resolveOrderOrNull() {
        if (TenantContext.isPlatformAdmin()) {
            return null;
        }
        String instanceId = TenantContext.getInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }
        MetrixInstance instance = metrixInstanceRepository.findById(instanceId).orElse(null);
        if (instance == null || instance.getOrderId() == null || instance.getOrderId().isBlank()) {
            return null;
        }
        return productOrderRepository.findById(instance.getOrderId()).orElse(null);
    }

    static List<String> resolveFeatureCodes(ProductOrder order) {
        ProductOrderPackageSnapshot snap = order.getPackageSnapshot();
        if (snap != null && snap.getFeatureCodes() != null && !snap.getFeatureCodes().isEmpty()) {
            return List.copyOf(LicenseFeatureCodes.asSet(snap.getFeatureCodes()).stream().toList());
        }
        String packageId = snap != null ? snap.getPackageId() : null;
        return LicenseFeatureCodes.defaultsForPackageId(packageId);
    }

    private static Integer snapshotMaxUsuarios(ProductOrder order) {
        ProductOrderPackageSnapshot snap = order.getPackageSnapshot();
        return snap != null ? snap.getMaxUsuarios() : null;
    }

    /**
     * Cupo operativo de sucursales.
     * <p>
     * En {@code PER_BRANCH} el cliente pagó N sucursales: ese N es el cupo
     * (acotado al máximo del plan). En planes de cuota fija ({@code FLAT_MONTHLY},
     * {@code PER_USER}) el precio no depende de cuántas sucursales anotó el
     * checkout: rige {@code maxSucursales} del snapshot (p. ej. Pro = 5).
     */
    static int resolveMaxSucursales(ProductOrder order) {
        ProductOrderPackageSnapshot snap = order.getPackageSnapshot();
        int planMax = snap != null && snap.getMaxSucursales() != null ? snap.getMaxSucursales() : 0;
        int contracted = order.getSucursalesContratadas();
        LicensePricingModel model = snap != null ? snap.getPricingModel() : null;

        if (model == LicensePricingModel.PER_BRANCH && contracted > 0) {
            return planMax > 0 ? Math.min(contracted, planMax) : contracted;
        }
        if (planMax > 0) {
            return planMax;
        }
        return Math.max(contracted, 0);
    }
}
