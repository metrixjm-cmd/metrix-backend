package com.metrix.api.platform.service;

import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.MetrixInstanceStatus;
import com.metrix.api.platform.model.ProductOrder;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetrixProvisioningService {

    private final MetrixInstanceRepository instanceRepository;
    private final TenantUserIndexService tenantUserIndexService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${metrix.platform.database-name:metrix_platform}")
    private String platformDatabaseName;

    public MetrixInstance provision(ProductOrder order, String numeroUsuario,
                                    String rawPassword, String adminNombre) {
        String instanceId = UUID.randomUUID().toString();
        String databaseName = buildDatabaseName(order.getEmpresaNombre(), instanceId);

        String nombreAdmin = adminNombre != null && !adminNombre.isBlank()
                ? adminNombre.trim()
                : order.getContactoNombre();

        MetrixInstance instance = MetrixInstance.builder()
                .id(instanceId)
                .databaseName(databaseName)
                .empresaNombre(order.getEmpresaNombre())
                .licensePackageId(order.getPackageSnapshot().getPackageId())
                .licensePackageNombre(order.getPackageSnapshot().getNombre())
                .orderId(order.getId())
                .adminNumeroUsuario(numeroUsuario)
                .adminNombre(nombreAdmin)
                .contactoEmail(order.getContactoEmail())
                .status(MetrixInstanceStatus.ACTIVE)
                .build();
        instance = instanceRepository.save(instance);

        createTenantAdmin(databaseName, numeroUsuario, rawPassword, nombreAdmin, order.getContactoEmail());

        tenantUserIndexService.index(numeroUsuario, databaseName, instanceId, order.getEmpresaNombre());

        log.info("[Provision] METRIX '{}' → BD {} (admin {})",
                order.getEmpresaNombre(), databaseName, numeroUsuario);

        return instance;
    }

    private void createTenantAdmin(String databaseName, String numeroUsuario,
                                   String rawPassword, String nombre, String email) {
        String previousDb = TenantContext.getDatabaseName();
        boolean previousPlatform = TenantContext.isPlatformAdmin();
        try {
            TenantContext.setPlatformAdmin(false);
            TenantContext.setDatabaseName(databaseName);

            User admin = User.builder()
                    .numeroUsuario(numeroUsuario)
                    .nombre(nombre)
                    .email(email)
                    .puesto("Administrador")
                    .password(passwordEncoder.encode(rawPassword))
                    .roles(Set.of(Role.ADMIN))
                    .activo(true)
                    .build();
            userRepository.save(admin);
        } finally {
            TenantContext.setDatabaseName(previousDb);
            TenantContext.setPlatformAdmin(previousPlatform);
        }
    }

    private String buildDatabaseName(String empresaNombre, String instanceId) {
        String slug = Normalizer.normalize(empresaNombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (slug.isBlank()) {
            slug = "cliente";
        }
        if (slug.length() > 24) {
            slug = slug.substring(0, 24);
        }
        String suffix = instanceId.replace("-", "").substring(0, 8);
        return "metrix_tenant_" + slug + "_" + suffix;
    }
}
