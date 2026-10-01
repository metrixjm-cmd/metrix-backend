package com.metrix.api.platform.service;

import com.metrix.api.dto.productos.CheckoutSessionResponse;
import com.metrix.api.dto.productos.SimulatedPaymentRequest;
import com.metrix.api.dto.productos.UserPackOrderResponse;
import com.metrix.api.dto.productos.UserPackStatusResponse;
import com.metrix.api.dto.productos.WebhookAckResponse;
import com.metrix.api.model.LicensePackage;
import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.config.MercadoPagoProperties;
import com.metrix.api.platform.license.UserPackPolicy;
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
import com.metrix.api.service.LicensePackageServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Un paquete extra de usuarios por licencia. El pago no se apila:
 * renueva 30 días y deja el cupo en el tamaño contratado en ese cobro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPackService {

    private final UserPackOrderRepository orderRepository;
    private final MetrixInstanceRepository instanceRepository;
    private final ProductOrderRepository productOrderRepository;
    private final LicensePackageRepository licensePackageRepository;
    private final PaymentGateway paymentGateway;
    private final MercadoPagoProperties paymentsProperties;
    private final ObjectProvider<MercadoPagoPaymentGateway> mercadoPagoGateway;

    public UserPackStatusResponse status() {
        return toStatus(requireInstance(), Instant.now());
    }

    public CheckoutSessionResponse checkout() {
        MetrixInstance instance = requireInstance();
        if (instance.getStatus() != MetrixInstanceStatus.ACTIVE) {
            throw new IllegalStateException("La licencia no está activa.");
        }
        Offer offer = resolveOffer(instance);
        if (!offer.disponible()) {
            throw new IllegalStateException(offer.motivo());
        }

        UserPackOrder order = orderRepository.save(UserPackOrder.builder()
                .instanceId(instance.getId())
                .licensePackageId(instance.getLicensePackageId())
                .usuarios(offer.usuarios())
                .monto(offer.precio())
                .moneda(offer.moneda())
                .paymentStatus(OrderPaymentStatus.NONE)
                .build());
        return startCheckout(order, instance);
    }

    public UserPackOrderResponse getOrder(String orderId) {
        return toOrderResponse(requireOwnOrder(orderId));
    }

    public UserPackOrderResponse pay(String orderId, SimulatedPaymentRequest request) {
        if (!paymentGateway.supportsSimulatedCardCharge()) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "El pago con tarjeta simulado no está disponible. Usa Checkout Pro.");
        }
        UserPackOrder order = requireOwnOrder(orderId);
        if (order.getPaymentStatus() == OrderPaymentStatus.APPROVED && order.getPaidAt() != null) {
            return toOrderResponse(order);
        }

        PaymentGateway.PaymentResult result = paymentGateway.charge(
                order.getMonto(), order.getMoneda(), request);
        if (!result.success()) {
            order.setPaymentStatus(OrderPaymentStatus.REJECTED);
            order.setPaymentProvider(PaymentProvider.SIMULATED);
            orderRepository.save(order);
            throw new IllegalStateException(result.message());
        }
        return toOrderResponse(applyApproved(
                order, result.reference(), null, PaymentProvider.SIMULATED));
    }

    public UserPackOrderResponse syncMercadoPagoPayment(String orderId, String paymentIdHint) {
        UserPackOrder order = requireOwnOrder(orderId);
        if (order.getPaymentStatus() == OrderPaymentStatus.APPROVED && order.getPaidAt() != null) {
            return toOrderResponse(order);
        }

        MercadoPagoPaymentGateway mp = mercadoPagoGateway.getIfAvailable();
        if (mp == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Mercado Pago no está activo en este entorno.");
        }

        String reference = UserPackPolicy.externalReference(order.getId());
        MercadoPagoPaymentGateway.MpPayment payment = null;
        if (paymentIdHint != null && !paymentIdHint.isBlank()) {
            payment = mp.fetchPayment(paymentIdHint.trim());
            if (payment != null
                    && payment.externalReference() != null
                    && !reference.equals(payment.externalReference())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El pago no corresponde a esta orden.");
            }
        }
        if (payment == null || !payment.isApproved()) {
            payment = mp.findApprovedByExternalReference(reference);
        }
        if (payment == null || !payment.isApproved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No hay un pago approved en Mercado Pago para este paquete todavía.");
        }
        if (!payment.matchesAmount(order.getMonto(), order.getMoneda())) {
            log.error("[MP] user-pack: monto/moneda no coinciden orden {} pago {}", orderId, payment.id());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El monto cobrado no coincide con el paquete.");
        }
        return toOrderResponse(applyApproved(
                order, "MP-" + payment.id(), payment.id(), PaymentProvider.MERCADOPAGO));
    }

    public boolean isPaymentAlreadyApplied(String mpPaymentId) {
        return mpPaymentId != null
                && !mpPaymentId.isBlank()
                && orderRepository.findByMpPaymentId(mpPaymentId).isPresent();
    }

    /**
     * @return vacío si el pago no es de un paquete de usuarios
     */
    public Optional<WebhookAckResponse> handleMercadoPagoPayment(MercadoPagoPaymentGateway.MpPayment payment) {
        if (payment == null) {
            return Optional.empty();
        }
        String orderId = UserPackPolicy.orderIdFromReference(payment.externalReference());
        if (orderId == null) {
            return Optional.empty();
        }
        UserPackOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("[MP] user-pack: orden {} no existe para pago {}", orderId, payment.id());
            return Optional.of(ack(orderId, false));
        }
        if (!payment.isApproved()) {
            if (order.getPaymentStatus() != OrderPaymentStatus.APPROVED) {
                order.setPaymentStatus(OrderPaymentStatus.REJECTED);
                order.setMpPaymentId(payment.id());
                order.setPaymentProvider(PaymentProvider.MERCADOPAGO);
                orderRepository.save(order);
            }
            return Optional.of(ack(order.getId(), false));
        }
        if (!payment.matchesAmount(order.getMonto(), order.getMoneda())) {
            log.error("[MP] user-pack: monto/moneda no coinciden orden {} pago {}", order.getId(), payment.id());
            return Optional.of(ack(order.getId(), false));
        }
        UserPackOrder updated = applyApproved(
                order, "MP-" + payment.id(), payment.id(), PaymentProvider.MERCADOPAGO);
        return Optional.of(ack(updated.getId(), true));
    }

    /**
     * Aplica el cobro. Si el paquete sigue vigente, el nuevo periodo
     * empieza cuando termina el actual. El tamaño no se suma: queda el del pago.
     */
    public UserPackOrder applyApproved(
            UserPackOrder order,
            String paymentReference,
            String mpPaymentId,
            PaymentProvider provider
    ) {
        if (order.getPaymentStatus() == OrderPaymentStatus.APPROVED && order.getPaidAt() != null) {
            return order;
        }
        MetrixInstance instance = instanceRepository.findById(order.getInstanceId())
                .orElseThrow(() -> new IllegalStateException("La licencia del paquete ya no existe."));

        Instant now = Instant.now();
        Instant start = now;
        if (instance.getExtraUsuariosHasta() != null && instance.getExtraUsuariosHasta().isAfter(now)) {
            start = instance.getExtraUsuariosHasta();
        }
        Instant end = start.plus(UserPackPolicy.PERIOD);

        instance.setExtraUsuarios(order.getUsuarios());
        instance.setExtraUsuariosHasta(end);
        instance.setExtraUsuariosOrderId(order.getId());
        instanceRepository.save(instance);

        order.setPaymentReference(paymentReference);
        if (mpPaymentId != null) {
            order.setMpPaymentId(mpPaymentId);
        }
        order.setPaymentProvider(provider);
        order.setPaymentStatus(OrderPaymentStatus.APPROVED);
        order.setPaidAt(now);
        order.setPeriodoInicio(start);
        order.setPeriodoFin(end);
        return orderRepository.save(order);
    }

    private CheckoutSessionResponse startCheckout(UserPackOrder order, MetrixInstance instance) {
        ProductOrder probe = ProductOrder.builder()
                .id(UserPackPolicy.externalReference(order.getId()))
                .totalCobrado(order.getMonto())
                .moneda(order.getMoneda())
                .contactoEmail(instance.getContactoEmail())
                .contactoNombre(instance.getEmpresaNombre())
                .preferenceId(order.getPreferenceId())
                .packageSnapshot(ProductOrderPackageSnapshot.builder()
                        .nombre("METRIX — " + order.getUsuarios() + " usuarios adicionales")
                        .build())
                .build();

        PaymentGateway.CheckoutUrls urls = buildCheckoutUrls(order.getId());
        PaymentGateway.CheckoutSession session = paymentGateway.createCheckout(probe, urls);
        order.setPreferenceId(session.preferenceId());
        order.setPaymentProvider(paymentGateway.provider());
        order.setPaymentStatus(OrderPaymentStatus.PENDING);
        orderRepository.save(order);

        return CheckoutSessionResponse.builder()
                .orderId(order.getId())
                .preferenceId(session.preferenceId())
                .initPoint(session.initPoint())
                .sandboxInitPoint(session.sandboxInitPoint())
                .status("PENDING_PAYMENT")
                .totalCobrado(order.getMonto())
                .moneda(order.getMoneda())
                .paymentProvider(order.getPaymentProvider())
                .paymentStatus(order.getPaymentStatus())
                .build();
    }

    private UserPackStatusResponse toStatus(MetrixInstance instance, Instant now) {
        Offer offer = resolveOffer(instance);
        int extra = UserPackPolicy.activeExtra(instance, now);
        boolean vigente = extra > 0;
        Integer planMax = offer.planMax();
        Integer efectivo = planMax == null ? null : planMax + extra;
        return UserPackStatusResponse.builder()
                .disponible(offer.disponible())
                .motivoNoDisponible(offer.disponible() ? null : offer.motivo())
                .usuariosPorPaquete(offer.usuarios())
                .precio(offer.precio())
                .moneda(offer.moneda())
                .maxUsuariosPlan(planMax)
                .usuariosExtraVigentes(extra)
                .maxUsuariosEfectivo(efectivo)
                .vigente(vigente)
                .vigenteHasta(vigente ? instance.getExtraUsuariosHasta() : null)
                .vencido(UserPackPolicy.expired(instance, now))
                .periodoDias((int) UserPackPolicy.PERIOD.toDays())
                .build();
    }

    private Offer resolveOffer(MetrixInstance instance) {
        LicensePackage pkg = instance.getLicensePackageId() == null
                ? null
                : licensePackageRepository.findById(instance.getLicensePackageId()).orElse(null);
        int usuarios = pkg == null
                ? UserPackPolicy.DEFAULT_PACK_SIZE
                : LicensePackageServiceImpl.resolveUsuariosPorPaquete(pkg);
        BigDecimal precio = pkg == null || pkg.getPrecioPaqueteUsuarios() == null
                ? BigDecimal.ZERO
                : pkg.getPrecioPaqueteUsuarios();
        String moneda = pkg != null && pkg.getMoneda() != null && !pkg.getMoneda().isBlank()
                ? pkg.getMoneda()
                : "MXN";

        Integer planMax = contractedMaxUsuarios(instance);
        boolean finite = planMax != null && planMax > 0;
        boolean priced = precio.compareTo(BigDecimal.ZERO) > 0 && usuarios >= 1;
        boolean disponible = finite && priced;
        String motivo = null;
        if (!disponible) {
            if (!finite) {
                motivo = "Tu plan incluye usuarios ilimitados.";
            } else {
                motivo = "Este plan no tiene a la venta el paquete de usuarios.";
            }
        }
        return new Offer(disponible, motivo, usuarios, precio, moneda, planMax);
    }

    private Integer contractedMaxUsuarios(MetrixInstance instance) {
        if (instance.getOrderId() == null || instance.getOrderId().isBlank()) {
            return null;
        }
        return productOrderRepository.findById(instance.getOrderId())
                .map(order -> order.getPackageSnapshot() == null
                        ? null
                        : order.getPackageSnapshot().getMaxUsuarios())
                .orElse(null);
    }

    private MetrixInstance requireInstance() {
        if (TenantContext.isPlatformAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El paquete de usuarios aplica a una licencia de cliente.");
        }
        String instanceId = TenantContext.getInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No hay una licencia asociada a esta sesión.");
        }
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Licencia no encontrada."));
    }

    private UserPackOrder requireOwnOrder(String orderId) {
        MetrixInstance instance = requireInstance();
        UserPackOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada."));
        if (!instance.getId().equals(order.getInstanceId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada.");
        }
        return order;
    }

    private PaymentGateway.CheckoutUrls buildCheckoutUrls(String orderId) {
        String front = trimTrailingSlash(paymentsProperties.getFrontendBaseUrl());
        String api = trimTrailingSlash(paymentsProperties.getPublicApiUrl());
        String returnBase = front + "/licencia/usuarios-extra/retorno/" + orderId;
        return new PaymentGateway.CheckoutUrls(
                returnBase + "?status=success",
                returnBase + "?status=failure",
                returnBase + "?status=pending",
                api + "/api/v1/webhooks/mercadopago"
        );
    }

    private static UserPackOrderResponse toOrderResponse(UserPackOrder order) {
        return UserPackOrderResponse.builder()
                .orderId(order.getId())
                .usuarios(order.getUsuarios())
                .monto(order.getMonto())
                .moneda(order.getMoneda())
                .paymentStatus(order.getPaymentStatus())
                .paymentProvider(order.getPaymentProvider())
                .preferenceId(order.getPreferenceId())
                .paidAt(order.getPaidAt())
                .periodoInicio(order.getPeriodoInicio())
                .periodoFin(order.getPeriodoFin())
                .build();
    }

    private static WebhookAckResponse ack(String orderId, boolean applied) {
        return WebhookAckResponse.builder()
                .received(true)
                .orderId(orderId)
                .applied(applied)
                .build();
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private record Offer(
            boolean disponible,
            String motivo,
            int usuarios,
            BigDecimal precio,
            String moneda,
            Integer planMax
    ) {}
}
