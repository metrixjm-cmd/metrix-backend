package com.metrix.api.platform.service;

import com.metrix.api.dto.productos.CreateProductOrderRequest;
import com.metrix.api.dto.productos.SimulatedPaymentRequest;
import com.metrix.api.model.LicensePackage;
import com.metrix.api.platform.config.MercadoPagoProperties;
import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.MetrixInstanceStatus;
import com.metrix.api.platform.model.OrderPaymentStatus;
import com.metrix.api.platform.model.PaymentProvider;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;

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
    @Mock private MercadoPagoProperties paymentsProperties;
    @Mock private ObjectProvider<MercadoPagoPaymentGateway> mercadoPagoGateway;
    @Mock private MercadoPagoWebhookSignatureValidator webhookSignatureValidator;

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

        when(paymentGateway.supportsSimulatedCardCharge()).thenReturn(true);
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
        assertEquals(OrderPaymentStatus.APPROVED, response.getPaymentStatus());
        verify(instanceRepository).save(any(MetrixInstance.class));
    }

    @Test
    void payOrder_goneWhenMercadoPagoProvider() {
        when(paymentGateway.supportsSimulatedCardCharge()).thenReturn(false);
        SimulatedPaymentRequest pay = new SimulatedPaymentRequest();
        pay.setCardholderName("Ana");
        pay.setCardNumber("4242424242424242");
        pay.setExpiryMonth("12");
        pay.setExpiryYear("2029");
        pay.setCvv("123");

        assertThrows(ResponseStatusException.class, () -> service.payOrder("ord-1", pay));
    }

    @Test
    void createCheckout_setsPreferencePending() {
        ProductOrder order = ProductOrder.builder()
                .id("ord-1")
                .status(ProductOrderStatus.PENDING_PAYMENT)
                .totalCobrado(BigDecimal.valueOf(1999))
                .moneda("MXN")
                .packageSnapshot(com.metrix.api.platform.model.ProductOrderPackageSnapshot.builder()
                        .packageId("base")
                        .nombre("Base")
                        .build())
                .build();
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));
        when(paymentsProperties.getFrontendBaseUrl()).thenReturn("http://localhost:4200");
        when(paymentsProperties.getPublicApiUrl()).thenReturn("http://localhost:8080");
        when(paymentGateway.provider()).thenReturn(PaymentProvider.SIMULATED);
        when(paymentGateway.createCheckout(any(), any())).thenReturn(
                new PaymentGateway.CheckoutSession("SIM-PREF-1",
                        "http://localhost:4200/productos/pago-retorno/ord-1?status=success",
                        "http://localhost:4200/productos/pago-retorno/ord-1?status=success"));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var session = service.createCheckout("ord-1");

        assertEquals("SIM-PREF-1", session.getPreferenceId());
        assertEquals(OrderPaymentStatus.PENDING, session.getPaymentStatus());
        assertNotNull(session.getInitPoint());
    }

    @Test
    void webhook_rejectsInvalidSignature() {
        when(webhookSignatureValidator.isValid(any(), any(), any())).thenReturn(false);
        assertThrows(ResponseStatusException.class,
                () -> service.handleMercadoPagoWebhook("pay-1", "req-1", "bad", "payment"));
    }

    @Test
    void webhook_appliesApprovedPaymentIdempotent() {
        when(webhookSignatureValidator.isValid(eq("pay-1"), eq("req-1"), eq("ok"))).thenReturn(true);
        when(orderRepository.findByMpPaymentId("pay-1")).thenReturn(Optional.empty());

        MercadoPagoPaymentGateway mp = org.mockito.Mockito.mock(MercadoPagoPaymentGateway.class);
        when(mercadoPagoGateway.getIfAvailable()).thenReturn(mp);
        when(mp.fetchPayment("pay-1")).thenReturn(new MercadoPagoPaymentGateway.MpPayment(
                "pay-1", "approved", "ord-1", BigDecimal.valueOf(1999), "MXN"));

        ProductOrder order = ProductOrder.builder()
                .id("ord-1")
                .status(ProductOrderStatus.PENDING_PAYMENT)
                .totalCobrado(BigDecimal.valueOf(1999))
                .moneda("MXN")
                .build();
        when(orderRepository.findById("ord-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var ack = service.handleMercadoPagoWebhook("pay-1", "req-1", "ok", "payment");
        assertTrue(ack.isApplied());
        assertEquals("ord-1", ack.getOrderId());

        when(orderRepository.findByMpPaymentId("pay-1")).thenReturn(Optional.of(order));
        var again = service.handleMercadoPagoWebhook("pay-1", "req-1", "ok", "payment");
        assertFalse(again.isApplied());
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
