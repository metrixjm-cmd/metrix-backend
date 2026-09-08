package com.metrix.api.platform.service;

import com.metrix.api.dto.productos.*;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.LicensePackage;
import com.metrix.api.platform.config.MercadoPagoProperties;
import com.metrix.api.platform.license.LicenseFeatureCodes;
import com.metrix.api.platform.license.LicenseTrialDays;
import com.metrix.api.platform.model.*;
import com.metrix.api.platform.repository.LicensePackageRepository;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import com.metrix.api.platform.repository.ProductOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductOrderService {

    private final ProductOrderRepository orderRepository;
    private final LicensePackageRepository licensePackageRepository;
    private final MetrixInstanceRepository instanceRepository;
    private final ProductPricingCalculator pricingCalculator;
    private final PaymentGateway paymentGateway;
    private final MetrixProvisioningService provisioningService;
    private final TenantUserIndexService tenantUserIndexService;
    private final MercadoPagoProperties paymentsProperties;
    private final ObjectProvider<MercadoPagoPaymentGateway> mercadoPagoGateway;
    private final MercadoPagoWebhookSignatureValidator webhookSignatureValidator;

    public ProductOrderResponse createOrder(CreateProductOrderRequest request) {
        LicensePackage pkg = licensePackageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Paquete no encontrado"));

        if (!pkg.isActivo()) {
            throw new IllegalStateException("El paquete no está disponible para compra.");
        }

        int sucursales = request.getSucursalesContratadas() != null
                ? request.getSucursalesContratadas() : 1;
        ProductPricingCalculator.PricingBreakdown pricing =
                pricingCalculator.calculate(request.getPackageId(), sucursales);

        ProductOrder order = ProductOrder.builder()
                .status(ProductOrderStatus.DRAFT)
                .packageSnapshot(toSnapshot(pkg))
                .empresaNombre(request.getEmpresaNombre().trim())
                .contactoNombre(request.getContactoNombre().trim())
                .contactoEmail(request.getContactoEmail().trim().toLowerCase(Locale.ROOT))
                .contactoTelefono(trimOrNull(request.getContactoTelefono()))
                .sucursalesContratadas(sucursales)
                .subtotalMensual(pricing.subtotalMensual())
                .cargoImplementacion(pricing.cargoImplementacion())
                .totalCobrado(pricing.totalCobrado())
                .moneda(pricing.moneda())
                .paymentStatus(OrderPaymentStatus.NONE)
                .build();

        order.setStatus(ProductOrderStatus.PENDING_PAYMENT);
        return toResponse(orderRepository.save(order));
    }

    public ProductOrderResponse getOrder(String orderId) {
        return toResponse(findOrder(orderId));
    }

    /**
     * Activa la prueba del paquete (sin cobro). El pago queda para
     * convertir el plan o reactivar cuando venza.
     */
    public ProductOrderResponse startTrial(String orderId) {
        ProductOrder order = findOrder(orderId);
        if (order.getStatus() == ProductOrderStatus.PROVISIONED && order.isOnTrial()) {
            return toResponse(order);
        }
        if (order.getStatus() == ProductOrderStatus.PROVISIONED
                || order.getStatus() == ProductOrderStatus.PAID) {
            throw new IllegalStateException("La orden ya fue pagada o provisionada.");
        }
        if (order.getStatus() == ProductOrderStatus.CANCELLED) {
            throw new IllegalStateException("La orden está cancelada.");
        }
        if (order.getStatus() == ProductOrderStatus.TRIAL) {
            return toResponse(order);
        }

        int days = snapshotDiasPrueba(order);
        if (days == 0) {
            throw new IllegalStateException("Este plan no incluye periodo de prueba. Completa el pago.");
        }

        Instant now = Instant.now();
        order.setStatus(ProductOrderStatus.TRIAL);
        order.setOnTrial(true);
        order.setTrialEndsAt(now.plus(days, ChronoUnit.DAYS));
        return toResponse(orderRepository.save(order));
    }

    /**
     * Crea preferencia Checkout Pro. No marca PAID.
     */
    public CheckoutSessionResponse createCheckout(String orderId) {
        ProductOrder order = findOrder(orderId);
        assertCheckoutable(order);

        if (order.getTotalCobrado() == null || order.getTotalCobrado().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("El monto de la orden no es válido para cobro.");
        }

        if (order.getPreferenceId() != null && !order.getPreferenceId().isBlank()
                && order.getPaymentStatus() == OrderPaymentStatus.PENDING
                && order.getPaymentProvider() == paymentGateway.provider()) {
            // Reuso superficial: el cliente puede volver a pedir init_point vía nueva preferencia.
            // Para simulated devolvemos la misma; para MP creamos de nuevo (preferencias son baratas).
            if (paymentGateway.provider() == PaymentProvider.SIMULATED) {
                PaymentGateway.CheckoutUrls urls = buildCheckoutUrls(orderId);
                PaymentGateway.CheckoutSession session = paymentGateway.createCheckout(order, urls);
                return toCheckoutResponse(order, session);
            }
        }

        PaymentGateway.CheckoutUrls urls = buildCheckoutUrls(orderId);
        PaymentGateway.CheckoutSession session = paymentGateway.createCheckout(order, urls);

        order.setPreferenceId(session.preferenceId());
        order.setPaymentProvider(paymentGateway.provider());
        order.setPaymentStatus(OrderPaymentStatus.PENDING);
        orderRepository.save(order);

        return toCheckoutResponse(order, session);
    }

    public ProductOrderResponse payOrder(String orderId, SimulatedPaymentRequest paymentRequest) {
        if (!paymentGateway.supportsSimulatedCardCharge()) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "El pago con tarjeta simulado no está disponible. Usa Checkout Pro.");
        }

        ProductOrder order = findOrder(orderId);
        if (order.getStatus() == ProductOrderStatus.CANCELLED) {
            throw new IllegalStateException("La orden está cancelada.");
        }
        boolean alreadyConverted = order.getPaidAt() != null
                && !order.isOnTrial()
                && order.getStatus() == ProductOrderStatus.PROVISIONED;
        if (alreadyConverted) {
            throw new IllegalStateException("La orden ya fue provisionada.");
        }

        PaymentGateway.PaymentResult result = paymentGateway.charge(
                order.getTotalCobrado(), order.getMoneda(), paymentRequest);

        if (!result.success()) {
            order.setPaymentStatus(OrderPaymentStatus.REJECTED);
            order.setPaymentProvider(PaymentProvider.SIMULATED);
            orderRepository.save(order);
            throw new IllegalStateException(result.message());
        }

        return toResponse(applyApprovedPayment(order, result.reference(), null, PaymentProvider.SIMULATED));
    }

    /**
     * Webhook MP: valida firma, consulta pago, aplica si approved y monto OK.
     */
    public WebhookAckResponse handleMercadoPagoWebhook(
            String dataId,
            String xRequestId,
            String xSignature,
            String payloadType
    ) {
        if (!webhookSignatureValidator.isValid(dataId, xRequestId, xSignature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Firma de webhook inválida.");
        }

        if (payloadType != null && !payloadType.isBlank()
                && !"payment".equalsIgnoreCase(payloadType)) {
            return WebhookAckResponse.builder().received(true).applied(false).build();
        }

        Optional<ProductOrder> byPayment = orderRepository.findByMpPaymentId(dataId);
        if (byPayment.isPresent()) {
            ProductOrder existing = byPayment.get();
            return WebhookAckResponse.builder()
                    .received(true)
                    .orderId(existing.getId())
                    .applied(false)
                    .build();
        }

        MercadoPagoPaymentGateway mp = mercadoPagoGateway.getIfAvailable();
        if (mp == null) {
            log.warn("[MP] Webhook recibido pero provider no es mercadopago");
            return WebhookAckResponse.builder().received(true).applied(false).build();
        }

        MercadoPagoPaymentGateway.MpPayment payment = mp.fetchPayment(dataId);
        if (payment == null) {
            return WebhookAckResponse.builder().received(true).applied(false).build();
        }

        if (!payment.isApproved()) {
            if (payment.externalReference() != null) {
                orderRepository.findById(payment.externalReference()).ifPresent(order -> {
                    if (order.getPaymentStatus() != OrderPaymentStatus.APPROVED) {
                        order.setPaymentStatus(OrderPaymentStatus.REJECTED);
                        order.setMpPaymentId(payment.id());
                        order.setPaymentProvider(PaymentProvider.MERCADOPAGO);
                        orderRepository.save(order);
                    }
                });
            }
            return WebhookAckResponse.builder()
                    .received(true)
                    .orderId(payment.externalReference())
                    .applied(false)
                    .build();
        }

        if (payment.externalReference() == null || payment.externalReference().isBlank()) {
            log.warn("[MP] Pago {} approved sin external_reference", payment.id());
            return WebhookAckResponse.builder().received(true).applied(false).build();
        }

        ProductOrder order = findOrder(payment.externalReference());
        if (!payment.matchesAmount(order.getTotalCobrado(), order.getMoneda())) {
            log.error("[MP] Monto/moneda no coinciden para orden {} pago {}", order.getId(), payment.id());
            return WebhookAckResponse.builder()
                    .received(true)
                    .orderId(order.getId())
                    .applied(false)
                    .build();
        }

        ProductOrder updated = applyApprovedPayment(
                order, "MP-" + payment.id(), payment.id(), PaymentProvider.MERCADOPAGO);
        return WebhookAckResponse.builder()
                .received(true)
                .orderId(updated.getId())
                .applied(true)
                .build();
    }

    /**
     * Reconcilia un cobro MP ya aprobado cuando el webhook no aplicó (firma, retraso, etc.).
     * Consulta la API de MP por external_reference (o paymentId opcional) y marca PAID.
     */
    public ProductOrderResponse syncMercadoPagoPayment(String orderId, String paymentIdHint) {
        ProductOrder order = findOrder(orderId);

        boolean alreadyPaid = order.getPaymentStatus() == OrderPaymentStatus.APPROVED
                && order.getPaidAt() != null
                && !order.isOnTrial();
        if (alreadyPaid) {
            return toResponse(order);
        }

        MercadoPagoPaymentGateway mp = mercadoPagoGateway.getIfAvailable();
        if (mp == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Mercado Pago no está activo en este entorno.");
        }

        MercadoPagoPaymentGateway.MpPayment payment = null;
        if (paymentIdHint != null && !paymentIdHint.isBlank()) {
            payment = mp.fetchPayment(paymentIdHint.trim());
            if (payment != null
                    && payment.externalReference() != null
                    && !payment.externalReference().equals(orderId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El pago no corresponde a esta orden.");
            }
        }
        if (payment == null || !payment.isApproved()) {
            payment = mp.findApprovedByExternalReference(orderId);
        }
        if (payment == null || !payment.isApproved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No hay un pago approved en Mercado Pago para esta orden todavía.");
        }
        if (!payment.matchesAmount(order.getTotalCobrado(), order.getMoneda())) {
            log.error("[MP] sync: monto/moneda no coinciden orden {} pago {}", orderId, payment.id());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El monto cobrado no coincide con la orden.");
        }

        return toResponse(applyApprovedPayment(
                order, "MP-" + payment.id(), payment.id(), PaymentProvider.MERCADOPAGO));
    }

    public ProvisionMetrixResponse provisionOrder(String orderId, ProvisionMetrixRequest request) {
        ProductOrder order = findOrder(orderId);
        if (order.getStatus() != ProductOrderStatus.PAID
                && order.getStatus() != ProductOrderStatus.TRIAL) {
            throw new IllegalStateException(
                    "La orden debe estar en prueba o pagada antes de crear el administrador.");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Las contraseñas no coinciden.");
        }

        String numeroUsuario = request.getNumeroUsuario().trim().toUpperCase(Locale.ROOT);
        validateUsernameAvailable(numeroUsuario);

        MetrixInstance instance = provisioningService.provision(
                order,
                numeroUsuario,
                request.getPassword(),
                trimOrNull(request.getAdminNombre())
        );

        order.setStatus(ProductOrderStatus.PROVISIONED);
        order.setInstanceId(instance.getId());
        orderRepository.save(order);

        return ProvisionMetrixResponse.builder()
                .instanceId(instance.getId())
                .databaseName(instance.getDatabaseName())
                .adminNumeroUsuario(instance.getAdminNumeroUsuario())
                .loginUrl("/auth/login")
                .message(order.isOnTrial()
                        ? "METRIX en periodo de prueba. Inicia sesión con tus credenciales."
                        : "METRIX creado correctamente. Inicia sesión con tus credenciales.")
                .build();
    }

    ProductOrder applyApprovedPayment(
            ProductOrder order,
            String paymentReference,
            String mpPaymentId,
            PaymentProvider provider
    ) {
        boolean alreadyConverted = order.getPaidAt() != null
                && !order.isOnTrial()
                && order.getStatus() == ProductOrderStatus.PROVISIONED
                && order.getPaymentStatus() == OrderPaymentStatus.APPROVED;
        if (alreadyConverted) {
            return order;
        }

        Instant now = Instant.now();
        order.setPaymentReference(paymentReference);
        if (mpPaymentId != null) {
            order.setMpPaymentId(mpPaymentId);
        }
        order.setPaymentProvider(provider);
        order.setPaymentStatus(OrderPaymentStatus.APPROVED);
        order.setPaidAt(now);
        order.setOnTrial(false);
        order.setTrialEndsAt(null);

        if (order.getInstanceId() != null && !order.getInstanceId().isBlank()) {
            convertInstanceToPaid(order.getInstanceId());
            order.setStatus(ProductOrderStatus.PROVISIONED);
        } else {
            order.setStatus(ProductOrderStatus.PAID);
        }
        return orderRepository.save(order);
    }

    private void assertCheckoutable(ProductOrder order) {
        if (order.getStatus() == ProductOrderStatus.CANCELLED) {
            throw new IllegalStateException("La orden está cancelada.");
        }
        boolean paidDone = order.getPaidAt() != null
                && order.getPaymentStatus() == OrderPaymentStatus.APPROVED
                && !order.isOnTrial();
        if (paidDone && order.getStatus() == ProductOrderStatus.PROVISIONED) {
            throw new IllegalStateException("La orden ya fue pagada.");
        }
        if (order.getStatus() == ProductOrderStatus.PAID
                && order.getPaymentStatus() == OrderPaymentStatus.APPROVED) {
            throw new IllegalStateException("La orden ya fue pagada.");
        }
    }

    private PaymentGateway.CheckoutUrls buildCheckoutUrls(String orderId) {
        String front = trimTrailingSlash(paymentsProperties.getFrontendBaseUrl());
        String api = trimTrailingSlash(paymentsProperties.getPublicApiUrl());
        String returnBase = front + "/productos/pago-retorno/" + orderId;
        return new PaymentGateway.CheckoutUrls(
                returnBase + "?status=success",
                returnBase + "?status=failure",
                returnBase + "?status=pending",
                api + "/api/v1/webhooks/mercadopago"
        );
    }

    private CheckoutSessionResponse toCheckoutResponse(
            ProductOrder order, PaymentGateway.CheckoutSession session) {
        return CheckoutSessionResponse.builder()
                .orderId(order.getId())
                .preferenceId(session.preferenceId())
                .initPoint(session.initPoint())
                .sandboxInitPoint(session.sandboxInitPoint())
                .status(order.getStatus().name())
                .totalCobrado(order.getTotalCobrado())
                .moneda(order.getMoneda())
                .paymentProvider(order.getPaymentProvider())
                .paymentStatus(order.getPaymentStatus())
                .build();
    }

    private void convertInstanceToPaid(String instanceId) {
        instanceRepository.findById(instanceId).ifPresent(instance -> {
            instance.setOnTrial(false);
            instance.setTrialEndsAt(null);
            instance.setSuspensionReason(null);
            instance.setStatus(MetrixInstanceStatus.ACTIVE);
            instanceRepository.save(instance);
        });
    }

    private void validateUsernameAvailable(String numeroUsuario) {
        tenantUserIndexService.assertNumeroUsuarioAvailable(numeroUsuario);
    }

    private ProductOrder findOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + orderId));
    }

    private ProductOrderPackageSnapshot toSnapshot(LicensePackage pkg) {
        return ProductOrderPackageSnapshot.builder()
                .packageId(pkg.getId())
                .nombre(pkg.getNombre())
                .etiqueta(pkg.getEtiqueta())
                .pricingModel(pkg.getPricingModel())
                .precioMensual(pkg.getPrecioMensual())
                .precioImplementacion(pkg.getPrecioImplementacion())
                .moneda(pkg.getMoneda())
                .maxUsuarios(pkg.getMaxUsuarios())
                .maxSucursales(pkg.getMaxSucursales())
                .diasPrueba(LicenseTrialDays.resolve(pkg.getDiasPrueba()))
                .featureCodes(pkg.getFeatureCodes() != null && !pkg.getFeatureCodes().isEmpty()
                        ? List.copyOf(pkg.getFeatureCodes())
                        : LicenseFeatureCodes.defaultsForPackageId(pkg.getId()))
                .accent(pkg.getAccent())
                .build();
    }

    private static int snapshotDiasPrueba(ProductOrder order) {
        ProductOrderPackageSnapshot snap = order.getPackageSnapshot();
        if (snap == null) {
            return LicenseTrialDays.DEFAULT;
        }
        return LicenseTrialDays.resolve(snap.getDiasPrueba());
    }

    private ProductOrderResponse toResponse(ProductOrder order) {
        ProductOrderPackageSnapshot snap = order.getPackageSnapshot();
        return ProductOrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .packageSnapshot(snap == null ? null : ProductOrderPackageSnapshotResponse.builder()
                        .packageId(snap.getPackageId())
                        .nombre(snap.getNombre())
                        .etiqueta(snap.getEtiqueta())
                        .pricingModel(snap.getPricingModel())
                        .precioMensual(snap.getPrecioMensual())
                        .precioImplementacion(snap.getPrecioImplementacion())
                        .moneda(snap.getMoneda())
                        .maxUsuarios(snap.getMaxUsuarios())
                        .maxSucursales(snap.getMaxSucursales())
                        .diasPrueba(LicenseTrialDays.resolve(snap.getDiasPrueba()))
                        .accent(snap.getAccent())
                        .build())
                .empresaNombre(order.getEmpresaNombre())
                .contactoNombre(order.getContactoNombre())
                .contactoEmail(order.getContactoEmail())
                .contactoTelefono(order.getContactoTelefono())
                .sucursalesContratadas(order.getSucursalesContratadas())
                .subtotalMensual(order.getSubtotalMensual())
                .cargoImplementacion(order.getCargoImplementacion())
                .totalCobrado(order.getTotalCobrado())
                .moneda(order.getMoneda())
                .paymentReference(order.getPaymentReference())
                .preferenceId(order.getPreferenceId())
                .mpPaymentId(order.getMpPaymentId())
                .paymentProvider(order.getPaymentProvider())
                .paymentStatus(order.getPaymentStatus() != null
                        ? order.getPaymentStatus() : OrderPaymentStatus.NONE)
                .paidAt(order.getPaidAt())
                .onTrial(order.isOnTrial())
                .trialEndsAt(order.getTrialEndsAt())
                .instanceId(order.getInstanceId())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
