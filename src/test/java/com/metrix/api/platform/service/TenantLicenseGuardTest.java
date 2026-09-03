package com.metrix.api.platform.service;

import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.license.LicenseFeatureCodes;
import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.ProductOrder;
import com.metrix.api.platform.model.ProductOrderPackageSnapshot;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import com.metrix.api.platform.repository.ProductOrderRepository;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantLicenseGuardTest {

    @Mock
    private MetrixInstanceRepository metrixInstanceRepository;
    @Mock
    private ProductOrderRepository productOrderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private TenantLicenseGuard guard;

    @BeforeEach
    void setTenant() {
        TenantContext.clear();
        TenantContext.setInstanceId("inst-1");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void withoutInstanceId_skipsLimits() {
        TenantContext.clear();
        assertDoesNotThrow(guard::assertCanCreateUser);
        assertDoesNotThrow(guard::assertCanCreateStore);
        verify(metrixInstanceRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void platformAdmin_skipsLimits() {
        TenantContext.setPlatformAdmin(true);
        assertDoesNotThrow(guard::assertCanCreateUser);
        verify(metrixInstanceRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createUser_allowsUnderLimit() {
        stubOrder(snapshot(15, 3), 2);
        when(userRepository.countByActivoTrue()).thenReturn(14L);
        assertDoesNotThrow(guard::assertCanCreateUser);
    }

    @Test
    void createUser_blocksAtLimit() {
        stubOrder(snapshot(15, 3), 2);
        when(userRepository.countByActivoTrue()).thenReturn(15L);
        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::assertCanCreateUser);
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("usuarios"));
    }

    @Test
    void createStore_blocksAtContractedBranches() {
        stubOrder(snapshot(50, 5), 2);
        when(storeRepository.countByActivoTrue()).thenReturn(2L);
        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::assertCanCreateStore);
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("sucursales"));
    }

    @Test
    void createStore_allowsUnderContractedBranches() {
        stubOrder(snapshot(50, 5), 2);
        when(storeRepository.countByActivoTrue()).thenReturn(1L);
        assertDoesNotThrow(guard::assertCanCreateStore);
    }

    @Test
    void nullMaxUsuarios_isUnlimited() {
        stubOrder(snapshot(null, 3), 1);
        assertDoesNotThrow(guard::assertCanCreateUser);
        verify(userRepository, never()).countByActivoTrue();
    }

    @Test
    void assertFeature_blocksWhenMissing() {
        stubOrder(snapshot(15, 3), 2);
        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> guard.assertFeature(LicenseFeatureCodes.EXAMS));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("EXAMS"));
    }

    @Test
    void assertFeature_allowsWhenInSnapshot() {
        ProductOrderPackageSnapshot snap = snapshot(50, 5);
        snap.setFeatureCodes(List.of(LicenseFeatureCodes.EXAMS, LicenseFeatureCodes.TRAININGS));
        stubOrder(snap, 2);
        assertDoesNotThrow(() -> guard.assertFeature(LicenseFeatureCodes.EXAMS));
    }

    @Test
    void resolveFeatures_fallsBackToPackageId() {
        stubOrder(snapshot(15, 2), 1); // base packageId in snapshot helper
        when(metrixInstanceRepository.findById("inst-1")).thenReturn(Optional.of(
                MetrixInstance.builder().id("inst-1").orderId("ord-1").build()));
        // re-stub with packageId base
        when(productOrderRepository.findById("ord-1")).thenReturn(Optional.of(
                ProductOrder.builder()
                        .id("ord-1")
                        .packageSnapshot(ProductOrderPackageSnapshot.builder()
                                .packageId("base")
                                .maxUsuarios(15)
                                .build())
                        .sucursalesContratadas(1)
                        .build()));
        List<String> features = guard.resolveLicensedFeaturesOrUnrestricted();
        org.junit.jupiter.api.Assertions.assertNotNull(features);
        org.junit.jupiter.api.Assertions.assertTrue(features.isEmpty());
    }

    private void stubOrder(ProductOrderPackageSnapshot snap, int sucursalesContratadas) {
        when(metrixInstanceRepository.findById("inst-1")).thenReturn(Optional.of(
                MetrixInstance.builder().id("inst-1").orderId("ord-1").build()));
        when(productOrderRepository.findById("ord-1")).thenReturn(Optional.of(
                ProductOrder.builder()
                        .id("ord-1")
                        .packageSnapshot(snap)
                        .sucursalesContratadas(sucursalesContratadas)
                        .build()));
    }

    private static ProductOrderPackageSnapshot snapshot(Integer maxUsuarios, Integer maxSucursales) {
        return ProductOrderPackageSnapshot.builder()
                .packageId("base")
                .maxUsuarios(maxUsuarios)
                .maxSucursales(maxSucursales)
                .build();
    }
}
