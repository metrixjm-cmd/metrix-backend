package com.metrix.api.platform.service;

import com.metrix.api.platform.EmpresaCodigos;
import com.metrix.api.platform.model.PlatformUser;
import com.metrix.api.platform.model.TenantAdminIndex;
import com.metrix.api.platform.repository.PlatformUserRepository;
import com.metrix.api.platform.repository.TenantAdminIndexRepository;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantLoginResolverTest {

    @Mock private PlatformUserRepository platformUserRepository;
    @Mock private TenantAdminIndexRepository tenantAdminIndexRepository;
    @Mock private UserRepository userRepository;

    private TenantLoginResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TenantLoginResolver(
                platformUserRepository, tenantAdminIndexRepository, userRepository);
        ReflectionTestUtils.setField(resolver, "mongoUri", "mongodb://localhost:27017/metrix_db");
    }

    @Test
    void metrixPlusAdmin001_isPlatform() {
        when(platformUserRepository.findByNumeroUsuario("ADMIN001"))
                .thenReturn(Optional.of(platformAdmin()));

        var resolution = resolver.resolve("METRIX", "ADMIN001");

        assertEquals(TenantLoginResolver.LoginType.PLATFORM, resolution.type());
        assertEquals(EmpresaCodigos.PLATFORM, resolution.codigoEmpresa());
    }

    @Test
    void tenantCodePlusAdmin001_isThatTenant() {
        when(tenantAdminIndexRepository.findByCodigoEmpresaAndNumeroUsuario("TACOS-A3F2", "ADMIN001"))
                .thenReturn(Optional.of(index("TACOS-A3F2", "metrix_tenant_tacos_a3f2c1d9", "inst-a")));

        var resolution = resolver.resolve("tacos-a3f2", "ADMIN001");

        assertEquals(TenantLoginResolver.LoginType.TENANT, resolution.type());
        assertEquals("metrix_tenant_tacos_a3f2c1d9", resolution.databaseName());
        assertEquals("TACOS-A3F2", resolution.codigoEmpresa());
    }

    @Test
    void sameUserDifferentTenantCode_doesNotCross() {
        when(tenantAdminIndexRepository.findByCodigoEmpresaAndNumeroUsuario("PIZZA-91BE", "ADMIN001"))
                .thenReturn(Optional.of(index("PIZZA-91BE", "metrix_tenant_pizza_91be4401", "inst-b")));

        var resolution = resolver.resolve("PIZZA-91BE", "ADMIN001");

        assertEquals("metrix_tenant_pizza_91be4401", resolution.databaseName());
        assertEquals("inst-b", resolution.instanceId());
    }

    @Test
    void unknownCode_isNotFound() {
        when(tenantAdminIndexRepository.findByCodigoEmpresaAndNumeroUsuario("NOPE-0000", "ADMIN001"))
                .thenReturn(Optional.empty());

        assertEquals(TenantLoginResolver.LoginType.NOT_FOUND,
                resolver.resolve("NOPE-0000", "ADMIN001").type());
    }

    @Test
    void tenantCodePlusUserOfOtherTenant_isNotFound() {
        when(tenantAdminIndexRepository.findByCodigoEmpresaAndNumeroUsuario("TACOS-A3F2", "GER-B"))
                .thenReturn(Optional.empty());

        assertEquals(TenantLoginResolver.LoginType.NOT_FOUND,
                resolver.resolve("TACOS-A3F2", "GER-B").type());
    }

    @Test
    void blankPlusUniqueIndexedUser_isLegacyTenant() {
        when(platformUserRepository.findByNumeroUsuario("QA99"))
                .thenReturn(Optional.empty());
        when(tenantAdminIndexRepository.findAllByNumeroUsuario("QA99"))
                .thenReturn(List.of(index("TACOS-A3F2", "metrix_tenant_tacos_a3f2c1d9", "inst-a")));

        var resolution = resolver.resolve("", "QA99");

        assertEquals(TenantLoginResolver.LoginType.TENANT, resolution.type());
        assertEquals("TACOS-A3F2", resolution.codigoEmpresa());
    }

    @Test
    void blankPlusDuplicateUsers_isNotFound() {
        when(platformUserRepository.findByNumeroUsuario("ADMIN001"))
                .thenReturn(Optional.empty());
        when(tenantAdminIndexRepository.findAllByNumeroUsuario("ADMIN001"))
                .thenReturn(List.of(
                        index("TACOS-A3F2", "db-a", "inst-a"),
                        index("PIZZA-91BE", "db-b", "inst-b")));

        assertEquals(TenantLoginResolver.LoginType.NOT_FOUND,
                resolver.resolve("", "ADMIN001").type());
    }

    @Test
    void metrixPlusTenantUser_isNotFound() {
        when(platformUserRepository.findByNumeroUsuario("QA99")).thenReturn(Optional.empty());

        assertEquals(TenantLoginResolver.LoginType.NOT_FOUND,
                resolver.resolve("METRIX", "QA99").type());
        assertNull(resolver.resolve("METRIX", "QA99").databaseName());
    }

    private static PlatformUser platformAdmin() {
        return PlatformUser.builder()
                .numeroUsuario("ADMIN001")
                .activo(true)
                .build();
    }

    private static TenantAdminIndex index(String codigo, String db, String instanceId) {
        return TenantAdminIndex.builder()
                .codigoEmpresa(codigo)
                .numeroUsuario("ADMIN001")
                .databaseName(db)
                .instanceId(instanceId)
                .empresaNombre("Resto")
                .build();
    }
}
