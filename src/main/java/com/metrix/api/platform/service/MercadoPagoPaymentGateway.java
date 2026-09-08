package com.metrix.api.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.metrix.api.platform.config.MercadoPagoProperties;
import com.metrix.api.platform.model.PaymentProvider;
import com.metrix.api.platform.model.ProductOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "metrix.payments.provider", havingValue = "mercadopago")
public class MercadoPagoPaymentGateway implements PaymentGateway {

    private final MercadoPagoProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    public CheckoutSession createCheckout(ProductOrder order, CheckoutUrls urls) {
        String token = properties.getMercadopago().getAccessToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("MP_ACCESS_TOKEN no configurado.");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("external_reference", order.getId());
        body.put("notification_url", urls.notificationUrl());
        body.put("auto_return", "approved");

        ObjectNode backUrls = body.putObject("back_urls");
        backUrls.put("success", urls.successUrl());
        backUrls.put("failure", urls.failureUrl());
        backUrls.put("pending", urls.pendingUrl());

        ArrayNode items = body.putArray("items");
        ObjectNode item = items.addObject();
        String title = order.getPackageSnapshot() != null && order.getPackageSnapshot().getNombre() != null
                ? "METRIX — " + order.getPackageSnapshot().getNombre()
                : "METRIX licencia";
        item.put("title", title);
        item.put("quantity", 1);
        item.put("currency_id", order.getMoneda() != null ? order.getMoneda() : "MXN");
        // Evitar double: MP compara montos exactos en el webhook/sync.
        item.put("unit_price", order.getTotalCobrado());

        if (order.getContactoEmail() != null) {
            ObjectNode payer = body.putObject("payer");
            payer.put("email", order.getContactoEmail());
            if (order.getContactoNombre() != null) {
                payer.put("name", order.getContactoNombre());
            }
        }

        try {
            JsonNode response = client().post()
                    .uri("/checkout/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.hasNonNull("id")) {
                throw new IllegalStateException("Mercado Pago no devolvió preference id.");
            }

            String preferenceId = response.get("id").asText();
            String initPoint = textOrNull(response, "init_point");
            String sandbox = textOrNull(response, "sandbox_init_point");
            if (initPoint == null || initPoint.isBlank()) {
                throw new IllegalStateException("Mercado Pago no devolvió init_point.");
            }
            return new CheckoutSession(preferenceId, initPoint, sandbox);
        } catch (RestClientException ex) {
            log.error("[MP] Error creando preferencia para orden {}: {}", order.getId(), ex.getMessage());
            throw new IllegalStateException("No se pudo crear el checkout en Mercado Pago.", ex);
        }
    }

    /**
     * Consulta un pago por id. Devuelve null si no existe o la API falla de forma recuperable.
     */
    public MpPayment fetchPayment(String paymentId) {
        try {
            JsonNode response = client().get()
                    .uri("/v1/payments/{id}", paymentId)
                    .retrieve()
                    .body(JsonNode.class);
            return toMpPayment(response, paymentId);
        } catch (RestClientException ex) {
            log.warn("[MP] No se pudo consultar pago {}: {}", paymentId, ex.getMessage());
            return null;
        }
    }

    /**
     * Busca el pago approved más reciente por {@code external_reference} (orderId).
     * Usado para reconciliar cuando el webhook falló (p. ej. firma) pero el cobro sí ocurrió.
     */
    public MpPayment findApprovedByExternalReference(String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return null;
        }
        try {
            JsonNode response = client().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/payments/search")
                            .queryParam("external_reference", externalReference)
                            .queryParam("sort", "date_created")
                            .queryParam("criteria", "desc")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.has("results") || !response.get("results").isArray()) {
                return null;
            }
            for (JsonNode row : response.get("results")) {
                MpPayment payment = toMpPayment(row, textOrNull(row, "id"));
                if (payment != null && payment.isApproved()) {
                    return payment;
                }
            }
            return null;
        } catch (RestClientException ex) {
            log.warn("[MP] No se pudo buscar pagos por external_reference {}: {}",
                    externalReference, ex.getMessage());
            return null;
        }
    }

    private static MpPayment toMpPayment(JsonNode response, String fallbackId) {
        if (response == null) {
            return null;
        }
        String id = textOrNull(response, "id");
        if (id == null || id.isBlank()) {
            id = fallbackId;
        }
        if (id == null || id.isBlank()) {
            return null;
        }
        String status = textOrNull(response, "status");
        String externalRef = textOrNull(response, "external_reference");
        String currency = textOrNull(response, "currency_id");
        BigDecimal amount = null;
        if (response.has("transaction_amount") && !response.get("transaction_amount").isNull()) {
            amount = response.get("transaction_amount").decimalValue();
        }
        return new MpPayment(id, status, externalRef, amount, currency);
    }

    private RestClient client() {
        return restClientBuilder
                .baseUrl(properties.getMercadopago().getApiBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getMercadopago().getAccessToken())
                .build();
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    public record MpPayment(
            String id,
            String status,
            String externalReference,
            BigDecimal transactionAmount,
            String currencyId
    ) {
        public boolean isApproved() {
            return status != null && "approved".equalsIgnoreCase(status.trim());
        }

        public boolean matchesAmount(BigDecimal expected, String expectedCurrency) {
            if (expected == null || transactionAmount == null) {
                return false;
            }
            if (transactionAmount.compareTo(expected) != 0) {
                return false;
            }
            if (expectedCurrency == null || currencyId == null) {
                return false;
            }
            return expectedCurrency.equalsIgnoreCase(currencyId.trim());
        }
    }
}
