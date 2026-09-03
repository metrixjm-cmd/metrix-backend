package com.metrix.api.platform.service;

import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.TenantDatabaseNames;
import com.metrix.api.platform.model.TenantAdminIndex;
import com.metrix.api.platform.repository.PlatformUserRepository;
import com.metrix.api.platform.repository.TenantAdminIndexRepository;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantUserIndexServiceTest {

    @Mock private TenantAdminIndexRepository indexRepository;
    @Mock private PlatformUserRepository platformUserRepository;
    @Mock private UserRepository userRepository;

    private TenantUserIndexService service;

    @BeforeEach
    void setUp() {
        TenantDatabaseNames names = new TenantDatabaseNames(
                "mongodb://localhost:27017/metrix_db", "metrix_platform");
        service = new TenantUserIndexService(
                indexRepository, platformUserRepository, userRepository, names);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void isTaken_whenIndexedInAnotherTenant() {
        when(platformUserRepository.existsByNumeroUsuario("GER-X")).thenReturn(false);
        when(indexRepository.existsByNumeroUsuario("GER-X")).thenReturn(true);

        assertTrue(service.isTaken("GER-X"));
    }

    @Test
    void assertAvailable_rejectsPlatformAdminNumero() {
        when(platformUserRepository.existsByNumeroUsuario("ADMIN001")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.assertNumeroUsuarioAvailable("ADMIN001"));
        assertEquals("El #Usuario ya está en uso. Elige otro.", ex.getMessage());
    }

    @Test
    void indexCurrentTenantUser_usesTenantContext() {
        TenantContext.setDatabaseName("metrix_tenant_acme_abcd1234");
        TenantContext.setInstanceId("inst-1");
        when(indexRepository.existsByNumeroUsuario("EJE001")).thenReturn(false);

        service.indexCurrentTenantUser("EJE001");

        ArgumentCaptor<TenantAdminIndex> captor = ArgumentCaptor.forClass(TenantAdminIndex.class);
        verify(indexRepository).save(captor.capture());
        assertEquals("EJE001", captor.getValue().getNumeroUsuario());
        assertEquals("metrix_tenant_acme_abcd1234", captor.getValue().getDatabaseName());
        assertEquals("inst-1", captor.getValue().getInstanceId());
    }

    @Test
    void index_skipsIfAlreadyPresent() {
        when(indexRepository.existsByNumeroUsuario("ADM-T")).thenReturn(true);

        service.index("ADM-T", "metrix_tenant_x_11111111", "id-1", "Acme");

        verify(indexRepository, never()).save(any());
    }

    @Test
    void isTaken_falseWhenEverywhereFree() {
        when(platformUserRepository.existsByNumeroUsuario("NEW1")).thenReturn(false);
        when(indexRepository.existsByNumeroUsuario("NEW1")).thenReturn(false);
        when(userRepository.existsByNumeroUsuario("NEW1")).thenReturn(false);

        assertFalse(service.isTaken("NEW1"));
    }
}
