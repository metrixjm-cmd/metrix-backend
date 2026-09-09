package com.metrix.api.platform.service;

import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.TenantDatabaseNames;
import com.metrix.api.platform.model.TenantAdminIndex;
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
    @Mock private UserRepository userRepository;

    private TenantUserIndexService service;

    @BeforeEach
    void setUp() {
        TenantDatabaseNames names = new TenantDatabaseNames(
                "mongodb://localhost:27017/metrix_db", "metrix_platform");
        service = new TenantUserIndexService(indexRepository, userRepository, names);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void isTaken_sameTenantCode() {
        when(indexRepository.existsByCodigoEmpresaAndNumeroUsuario("TACOS-A3F2", "GER001"))
                .thenReturn(true);

        assertTrue(service.isTaken("GER001", "TACOS-A3F2"));
    }

    @Test
    void isTaken_otherTenantDoesNotBlock() {
        TenantContext.setCodigoEmpresa("PIZZA-91BE");
        when(indexRepository.existsByCodigoEmpresaAndNumeroUsuario("PIZZA-91BE", "GER001"))
                .thenReturn(false);
        when(userRepository.existsByNumeroUsuario("GER001")).thenReturn(false);

        assertFalse(service.isTaken("GER001"));
    }

    @Test
    void assertAvailable_allowsPlatformAdminNumeroInTenant() {
        when(indexRepository.existsByCodigoEmpresaAndNumeroUsuario("TACOS-A3F2", "ADMIN001"))
                .thenReturn(false);
        when(userRepository.existsByNumeroUsuario("ADMIN001")).thenReturn(false);

        service.assertNumeroUsuarioAvailable("ADMIN001", "TACOS-A3F2");
    }

    @Test
    void assertAvailable_rejectsDuplicateInSameTenant() {
        when(indexRepository.existsByCodigoEmpresaAndNumeroUsuario("TACOS-A3F2", "GER001"))
                .thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.assertNumeroUsuarioAvailable("GER001", "TACOS-A3F2"));
        assertEquals("El #Usuario ya está en uso. Elige otro.", ex.getMessage());
    }

    @Test
    void indexCurrentTenantUser_usesTenantContext() {
        TenantContext.setDatabaseName("metrix_tenant_acme_abcd1234");
        TenantContext.setInstanceId("inst-1");
        TenantContext.setCodigoEmpresa("ACME-ABCD");
        when(indexRepository.existsByCodigoEmpresaAndNumeroUsuario("ACME-ABCD", "EJE001"))
                .thenReturn(false);

        service.indexCurrentTenantUser("EJE001");

        ArgumentCaptor<TenantAdminIndex> captor = ArgumentCaptor.forClass(TenantAdminIndex.class);
        verify(indexRepository).save(captor.capture());
        assertEquals("EJE001", captor.getValue().getNumeroUsuario());
        assertEquals("ACME-ABCD", captor.getValue().getCodigoEmpresa());
        assertEquals("metrix_tenant_acme_abcd1234", captor.getValue().getDatabaseName());
        assertEquals("inst-1", captor.getValue().getInstanceId());
    }

    @Test
    void index_skipsIfAlreadyPresent() {
        when(indexRepository.existsByCodigoEmpresaAndNumeroUsuario("X-1111", "ADM-T"))
                .thenReturn(true);

        service.index("ADM-T", "metrix_tenant_x_11111111", "id-1", "Acme", "X-1111");

        verify(indexRepository, never()).save(any());
    }

    @Test
    void isTaken_falseWhenTenantAndLocalFree() {
        when(indexRepository.existsByCodigoEmpresaAndNumeroUsuario("ACME-ABCD", "NEW1"))
                .thenReturn(false);
        when(userRepository.existsByNumeroUsuario("NEW1")).thenReturn(false);

        assertFalse(service.isTaken("NEW1", "ACME-ABCD"));
    }
}
