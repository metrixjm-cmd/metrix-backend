package com.metrix.api.platform.service;

import com.metrix.api.dto.productos.MetrixInstanceResponse;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.platform.TenantDatabaseNames;
import com.metrix.api.platform.license.LicenseFeatureCodes;
import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.MetrixInstanceStatus;
import com.metrix.api.platform.model.MetrixInstanceSuspensionReason;
import com.metrix.api.platform.model.ProductOrder;
import com.metrix.api.platform.model.ProductOrderPackageSnapshot;
import com.metrix.api.platform.model.ProductOrderStatus;
import com.metrix.api.platform.model.TenantAdminIndex;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import com.metrix.api.platform.repository.ProductOrderRepository;
import com.metrix.api.platform.repository.TenantAdminIndexRepository;
import com.mongodb.client.MongoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private static final String TENANT_DB_PREFIX = "metrix_tenant_";

    private final MetrixInstanceRepository instanceRepository;
    private final ProductOrderRepository productOrderRepository;
    private final TenantAdminIndexRepository tenantAdminIndexRepository;
    private final TenantDatabaseNames tenantDatabaseNames;
    private final MongoClient mongoClient;

    public List<MetrixInstanceResponse> listInstances() {
        return instanceRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public MetrixInstanceResponse updateStatus(String instanceId, MetrixInstanceStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("El estado es obligatorio.");
        }
        MetrixInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instancia no encontrada: " + instanceId));
        instance.setStatus(status);
        if (status == MetrixInstanceStatus.ACTIVE) {
            instance.setSuspensionReason(null);
            instance.setOnTrial(false);
            instance.setTrialEndsAt(null);
        } else {
            instance.setSuspensionReason(MetrixInstanceSuspensionReason.MANUAL);
        }
        return toResponse(instanceRepository.save(instance));
    }

    /**
     * Elimina el registro de instancia, el índice de login, marca la orden como cancelada
     * y elimina la BD Mongo del tenant (solo {@code metrix_tenant_*}).
     */
    public void deleteInstance(String instanceId) {
        MetrixInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instancia no encontrada: " + instanceId));

        String databaseName = instance.getDatabaseName();
        assertTenantDatabaseDroppable(databaseName);

        String orderId = instance.getOrderId();

        List<TenantAdminIndex> indexRows = tenantAdminIndexRepository.findByInstanceId(instanceId);
        if (!indexRows.isEmpty()) {
            tenantAdminIndexRepository.deleteAll(indexRows);
        }

        if (orderId != null && !orderId.isBlank()) {
            productOrderRepository.findById(orderId).ifPresent(order -> {
                order.setStatus(ProductOrderStatus.CANCELLED);
                order.setInstanceId(null);
                productOrderRepository.save(order);
            });
        }

        instanceRepository.delete(instance);
        dropTenantDatabaseIfSafe(databaseName);

        log.info("[Platform] Instancia {} eliminada (empresa={}, db={})",
                instanceId, instance.getEmpresaNombre(), databaseName);
    }

    private void assertTenantDatabaseDroppable(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            return;
        }
        if (databaseName.equals(tenantDatabaseNames.getPlatformDatabase())
                || databaseName.equals(tenantDatabaseNames.getDefaultOperationalDatabase())) {
            throw new IllegalStateException("No se puede eliminar la base de datos de plataforma u operativa.");
        }
    }

    private void dropTenantDatabaseIfSafe(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            return;
        }
        if (!databaseName.startsWith(TENANT_DB_PREFIX)) {
            log.warn("[Platform] No se elimina BD '{}': no es tenant", databaseName);
            return;
        }
        assertTenantDatabaseDroppable(databaseName);
        mongoClient.getDatabase(databaseName).drop();
        log.info("[Platform] BD tenant eliminada: {}", databaseName);
    }

    public boolean isSuspended(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return false;
        }
        return instanceRepository.findById(instanceId)
                .map(this::expireTrialIfNeeded)
                .map(i -> i.getStatus() == MetrixInstanceStatus.SUSPENDED)
                .orElse(false);
    }

    public String suspensionMessage(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return "Esta instancia METRIX está suspendida. Contacta a soporte para reactivarla.";
        }
        return instanceRepository.findById(instanceId)
                .map(this::expireTrialIfNeeded)
                .filter(i -> i.getStatus() == MetrixInstanceStatus.SUSPENDED)
                .map(i -> i.getSuspensionReason() == MetrixInstanceSuspensionReason.TRIAL_EXPIRED
                        ? "Tu periodo de prueba terminó. Completa el pago simulado para reactivar la cuenta."
                        : "Esta instancia METRIX está suspendida. Contacta a soporte para reactivarla.")
                .orElse("Esta instancia METRIX está suspendida. Contacta a soporte para reactivarla.");
    }

    public int expireDueTrials() {
        Instant now = Instant.now();
        List<MetrixInstance> due = instanceRepository
                .findByOnTrialTrueAndStatusAndTrialEndsAtBefore(MetrixInstanceStatus.ACTIVE, now);
        int count = 0;
        for (MetrixInstance instance : due) {
            expireTrialIfNeeded(instance);
            count++;
        }
        return count;
    }

    public TenantBillingView billingView(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return TenantBillingView.empty();
        }
        return instanceRepository.findById(instanceId)
                .map(this::expireTrialIfNeeded)
                .map(i -> new TenantBillingView(i.isOnTrial(), i.getTrialEndsAt(), i.getOrderId()))
                .orElse(TenantBillingView.empty());
    }

    public record TenantBillingView(boolean onTrial, Instant trialEndsAt, String orderId) {
        static TenantBillingView empty() {
            return new TenantBillingView(false, null, null);
        }
    }

    private MetrixInstance expireTrialIfNeeded(MetrixInstance instance) {
        if (instance.isOnTrial()
                && instance.getStatus() == MetrixInstanceStatus.ACTIVE
                && instance.getTrialEndsAt() != null
                && Instant.now().isAfter(instance.getTrialEndsAt())) {
            instance.setStatus(MetrixInstanceStatus.SUSPENDED);
            instance.setSuspensionReason(MetrixInstanceSuspensionReason.TRIAL_EXPIRED);
            return instanceRepository.save(instance);
        }
        return instance;
    }

    private MetrixInstanceResponse toResponse(MetrixInstance instance) {
        ProductOrder order = null;
        if (instance.getOrderId() != null && !instance.getOrderId().isBlank()) {
            order = productOrderRepository.findById(instance.getOrderId()).orElse(null);
        }
        ProductOrderPackageSnapshot snap = order != null ? order.getPackageSnapshot() : null;

        Integer maxUsuarios = snap != null ? snap.getMaxUsuarios() : null;
        Integer maxSucursales = snap != null ? snap.getMaxSucursales() : null;
        Integer sucursalesContratadas = order != null ? order.getSucursalesContratadas() : null;
        List<String> featureCodes = List.of();
        if (snap != null && snap.getFeatureCodes() != null && !snap.getFeatureCodes().isEmpty()) {
            featureCodes = List.copyOf(snap.getFeatureCodes());
        } else if (instance.getLicensePackageId() != null) {
            featureCodes = LicenseFeatureCodes.defaultsForPackageId(instance.getLicensePackageId());
        }

        return MetrixInstanceResponse.builder()
                .id(instance.getId())
                .databaseName(instance.getDatabaseName())
                .empresaNombre(instance.getEmpresaNombre())
                .licensePackageId(instance.getLicensePackageId())
                .licensePackageNombre(instance.getLicensePackageNombre())
                .orderId(instance.getOrderId())
                .adminNumeroUsuario(instance.getAdminNumeroUsuario())
                .adminNombre(instance.getAdminNombre())
                .contactoEmail(instance.getContactoEmail())
                .status(instance.getStatus())
                .onTrial(instance.isOnTrial())
                .trialEndsAt(instance.getTrialEndsAt())
                .suspensionReason(instance.getSuspensionReason())
                .createdAt(instance.getCreatedAt())
                .maxUsuarios(maxUsuarios)
                .maxSucursales(maxSucursales)
                .sucursalesContratadas(sucursalesContratadas)
                .featureCodes(featureCodes)
                .paidAt(order != null ? order.getPaidAt() : null)
                .build();
    }
}
