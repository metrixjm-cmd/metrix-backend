package com.metrix.api.platform.service;

import com.metrix.api.dto.productos.CreateProductOrderRequest;
import com.metrix.api.dto.productos.SimulatedPaymentRequest;
import com.metrix.api.model.LicensePackage;
import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.MetrixInstanceStatus;
import com.metrix.api.platform.model.ProductOrder;
import com.metrix.api.platform.model.ProductOrderStatus;
import com.metrix.api.platform.repository.LicensePackageRepository;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import com.metrix.api.platform.repository.ProductOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductOrderServiceTest {

    @Mock private ProductOrderRepository orderRepository;
    @Mock private LicensePackageRepository licensePackageRepository;
    @Mock private MetrixInstanceRepository instanceRepository;
    @Mock private ProductPricingCalculator pricingCalculator;
    @Mock private PaymentGateway paymentGateway;
    @Mock private MetrixProvisioningService provisioningService;
    @Mock private TenantUserIndexService tenantUserIndexService;

    @InjectMocks private ProductOrderService service;

    private LicensePackage pkg;
    private CreateProductOrderRequest createRequest;

    @BeforeEach
    void setUp() {
        pkg = LicensePackage.builder()
                .id("base")
                .nombre("METRIX Base")
                .activo(true)
                .diasPrueba(7)
                .precioMensual(BigDecimal.valueOf(1999))
                .precioImplementacion(BigDecimal.ZERO)
                .moneda("MXN")
                .build();
        createRequest = new CreateProductOrderRequest();
        createRequest.setPackageId("base");
        createRequest.setEmpresaNombre("Resto Demo");
        createRequest.setContactoNombre("Ana");
        createRequest.setContactoEmail("ana@demo.test");
        createRequest.setSucursalesContratadas(1);
    }

    @Test
    void startTrial_setsSevenDays() {
        ProductOrder pending = ProductOrder.builder()
                .id("ord-1")
                .status(ProductOrderStatus.PENDING_PAYMENT)
                .packageSnapshot(com.metrix.api.platform.model.ProductOrderPackageSnapshot.builder()
                        .packageId("base")
                        .diasPrueba(7)
                        .build())
                .build();
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(pending));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.startTrial("ord-1");

        assertEquals(ProductOrderStatus.TRIAL, response.getStatus());
        assertTrue(response.isOnTrial());
        assertNotNull(response.getTrialEndsAt());
        assertTrue(response.getTrialEndsAt().isAfter(Instant.now().plus(6, ChronoUnit.DAYS)));
    }

    @Test
    void startTrial_rejectsWhenNoTrialDays() {
        ProductOrder pending = ProductOrder.builder()
                .id("ord-1")
                .status(ProductOrderStatus.PENDING_PAYMENT)
                .packageSnapshot(com.metrix.api.platform.model.ProductOrderPackageSnapshot.builder()
                        .packageId("base")
                        .diasPrueba(0)
                        .build())
                .build();
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(pending));

        assertThrows(IllegalStateException.class, () -> service.startTrial("ord-1"));
    }

    @Test
    void payOrder_convertsExistingTrialInstance() {
        ProductOrder order = ProductOrder.builder()
                .id("ord-1")
                .status(ProductOrderStatus.PROVISIONED)
                .onTrial(true)
                .instanceId("inst-1")
                .totalCobrado(BigDecimal.TEN)
                .moneda("MXN")
                .packageSnapshot(com.metrix.api.platform.model.ProductOrderPackageSnapshot.builder()
                        .packageId("base")
                        .build())
                .build();
        SimulatedPaymentRequest pay = new SimulatedPaymentRequest();
        pay.setCardholderName("Ana");
        pay.setCardNumber("4242424242424242");
        pay.setExpiryMonth("12");
        pay.setExpiryYear("2029");
        pay.setCvv("123");

        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));
        when(paymentGateway.charge(any(), eq("MXN"), any()))
                .thenReturn(new PaymentGateway.PaymentResult(true, "SIM-1", "ok"));
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(
                MetrixInstance.builder()
                        .id("inst-1")
                        .status(MetrixInstanceStatus.SUSPENDED)
                        .onTrial(true)
                        .build()));
        when(instanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.payOrder("ord-1", pay);

        assertFalse(response.isOnTrial());
        assertEquals(ProductOrderStatus.PROVISIONED, response.getStatus());
        verify(instanceRepository).save(any(MetrixInstance.class));
    }

    @Test
    void createOrder_persistsPendingPayment() {
        when(licensePackageRepository.findById("base")).thenReturn(Optional.of(pkg));
        when(pricingCalculator.calculate(eq("base"), anyInt())).thenReturn(
                new ProductPricingCalculator.PricingBreakdown(
                        BigDecimal.valueOf(1999), BigDecimal.ZERO, BigDecimal.valueOf(1999), "MXN"));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            ProductOrder o = inv.getArgument(0);
            o.setId("ord-new");
            return o;
        });

        var response = service.createOrder(createRequest);
        assertEquals(ProductOrderStatus.PENDING_PAYMENT, response.getStatus());
        assertEquals("ord-new", response.getId());
    }
}
