package com.metrix.api.platform.service;

import com.metrix.api.model.LicensePackage;
import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.config.MercadoPagoProperties;
import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.MetrixInstanceStatus;
import com.metrix.api.platform.model.OrderPaymentStatus;
import com.metrix.api.platform.model.PaymentProvider;
import com.metrix.api.platform.model.ProductOrder;
import com.metrix.api.platform.model.ProductOrderPackageSnapshot;
import com.metrix.api.platform.model.UserPackOrder;
import com.metrix.api.platform.repository.LicensePackageRepository;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import com.metrix.api.platform.repository.ProductOrderRepository;
import com.metrix.api.platform.repository.UserPackOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPackServiceTest {

    @Mock private UserPackOrderRepository orderRepository;
    @Mock private MetrixInstanceRepository instanceRepository;
    @Mock private ProductOrderRepository productOrderRepository;
    @Mock private LicensePackageRepository licensePackageRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private MercadoPagoProperties paymentsProperties;
    @Mock private ObjectProvider<MercadoPagoPaymentGateway> mercadoPagoGateway;

    @InjectMocks private UserPackService service;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void applyApproved_extendsFromCurrentEnd_andReplacesSize() {
        Instant until = Instant.now().plus(10, ChronoUnit.DAYS);
        MetrixInstance instance = MetrixInstance.builder()
                .id("inst-1")
                .extraUsuarios(10)
                .extraUsuariosHasta(until)
                .build();
        UserPackOrder order = UserPackOrder.builder()
                .id("up-1")
                .instanceId("inst-1")
                .usuarios(12)
                .monto(new BigDecimal("499"))
                .moneda("MXN")
                .paymentStatus(OrderPaymentStatus.PENDING)
                .build();
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(instance));
        when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserPackOrder paid = service.applyApproved(order, "SIM-1", null, PaymentProvider.SIMULATED);

        assertEquals(12, instance.getExtraUsuarios());
        assertTrue(instance.getExtraUsuariosHasta().isAfter(until.plus(29, ChronoUnit.DAYS)));
        assertEquals(OrderPaymentStatus.APPROVED, paid.getPaymentStatus());
        assertEquals("up-1", instance.getExtraUsuariosOrderId());

        Instant end = instance.getExtraUsuariosHasta();
        service.applyApproved(paid, "SIM-1", null, PaymentProvider.SIMULATED);
        assertEquals(end, instance.getExtraUsuariosHasta());
        verify(instanceRepository, times(1)).save(any());
    }

    @Test
    void status_hidesPackWhenPriceIsZero() {
        TenantContext.setInstanceId("inst-1");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(activeInstance()));
        when(productOrderRepository.findById("ord-1")).thenReturn(Optional.of(orderWithMax(15)));
        when(licensePackageRepository.findById("base")).thenReturn(Optional.of(
                LicensePackage.builder()
                        .id("base")
                        .moneda("MXN")
                        .usuariosPorPaquete(10)
                        .precioPaqueteUsuarios(BigDecimal.ZERO)
                        .build()));

        var status = service.status();
        assertFalse(status.isDisponible());
        assertEquals(10, status.getUsuariosPorPaquete());
        assertEquals(15, status.getMaxUsuariosPlan());
    }

    @Test
    void checkout_rejectsWhenPackIsNotForSale() {
        TenantContext.setInstanceId("inst-1");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(activeInstance()));
        when(productOrderRepository.findById("ord-1")).thenReturn(Optional.of(orderWithMax(15)));
        when(licensePackageRepository.findById("base")).thenReturn(Optional.of(
                LicensePackage.builder().id("base").precioPaqueteUsuarios(BigDecimal.ZERO).build()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::checkout);
        assertTrue(ex.getMessage().contains("no tiene a la venta"));
        verify(orderRepository, never()).save(any());
    }

    private static MetrixInstance activeInstance() {
        return MetrixInstance.builder()
                .id("inst-1")
                .orderId("ord-1")
                .licensePackageId("base")
                .status(MetrixInstanceStatus.ACTIVE)
                .build();
    }

    private static ProductOrder orderWithMax(int max) {
        return ProductOrder.builder()
                .id("ord-1")
                .packageSnapshot(ProductOrderPackageSnapshot.builder().maxUsuarios(max).build())
                .build();
    }
}
