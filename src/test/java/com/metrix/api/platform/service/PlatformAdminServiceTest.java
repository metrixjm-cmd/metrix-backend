package com.metrix.api.platform.service;

import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.MetrixInstanceStatus;
import com.metrix.api.platform.model.ProductOrder;
import com.metrix.api.platform.model.ProductOrderPackageSnapshot;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import com.metrix.api.platform.repository.ProductOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminServiceTest {

    @Mock
    private MetrixInstanceRepository instanceRepository;
    @Mock
    private ProductOrderRepository productOrderRepository;

    @InjectMocks
    private PlatformAdminService service;

    @Test
    void suspend_updatesStatus() {
        MetrixInstance instance = MetrixInstance.builder()
                .id("inst-1")
                .empresaNombre("Demo SA")
                .licensePackageId("base")
                .licensePackageNombre("METRIX Base")
                .orderId("ord-1")
                .status(MetrixInstanceStatus.ACTIVE)
                .build();
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productOrderRepository.findById("ord-1")).thenReturn(Optional.of(
                ProductOrder.builder()
                        .id("ord-1")
                        .sucursalesContratadas(2)
                        .packageSnapshot(ProductOrderPackageSnapshot.builder()
                                .packageId("base")
                                .maxUsuarios(15)
                                .maxSucursales(2)
                                .build())
                        .build()));

        var response = service.updateStatus("inst-1", MetrixInstanceStatus.SUSPENDED);

        assertEquals(MetrixInstanceStatus.SUSPENDED, response.getStatus());
        assertEquals(15, response.getMaxUsuarios());
        assertEquals(2, response.getSucursalesContratadas());
        verify(instanceRepository).save(any(MetrixInstance.class));
    }

    @Test
    void isSuspended_trueWhenSuspended() {
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(
                MetrixInstance.builder().id("inst-1").status(MetrixInstanceStatus.SUSPENDED).build()));
        assertTrue(service.isSuspended("inst-1"));
    }

    @Test
    void isSuspended_falseWhenActiveOrMissing() {
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(
                MetrixInstance.builder().id("inst-1").status(MetrixInstanceStatus.ACTIVE).build()));
        assertFalse(service.isSuspended("inst-1"));
        assertFalse(service.isSuspended(null));
        assertFalse(service.isSuspended(" "));
    }

    @Test
    void list_enrichesFeatureCodesFromPackageId() {
        when(instanceRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                MetrixInstance.builder()
                        .id("inst-pro")
                        .licensePackageId("pro")
                        .licensePackageNombre("METRIX Pro")
                        .status(MetrixInstanceStatus.ACTIVE)
                        .build()));

        var list = service.listInstances();
        assertEquals(1, list.size());
        assertTrue(list.get(0).getFeatureCodes().contains("EXAMS"));
    }
}
