package com.metrix.api.platform.service;

import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.ProductOrder;
import com.metrix.api.platform.model.ProductOrderPackageSnapshot;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import com.metrix.api.platform.repository.ProductOrderRepository;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Aplica los cupos del paquete contratado (snapshot de la orden) al crear
 * usuarios o sucursales en un tenant provisionado.
 * <p>
 * Sin {@code instanceId} (demo legacy / Admin 0 en {@code metrix_db}) no aplica límites.
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

    private static Integer snapshotMaxUsuarios(ProductOrder order) {
        ProductOrderPackageSnapshot snap = order.getPackageSnapshot();
        return snap != null ? snap.getMaxUsuarios() : null;
    }

    /**
     * Cupo efectivo: sucursales contratadas en la compra; si falta, el max del snapshot.
     */
    static int resolveMaxSucursales(ProductOrder order) {
        if (order.getSucursalesContratadas() > 0) {
            return order.getSucursalesContratadas();
        }
        ProductOrderPackageSnapshot snap = order.getPackageSnapshot();
        if (snap != null && snap.getMaxSucursales() != null && snap.getMaxSucursales() > 0) {
            return snap.getMaxSucursales();
        }
        return 0;
    }
}
