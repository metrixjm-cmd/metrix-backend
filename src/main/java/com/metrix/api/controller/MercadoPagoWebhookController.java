package com.metrix.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.metrix.api.dto.productos.WebhookAckResponse;
import com.metrix.api.platform.service.ProductOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Notificaciones de pasarelas de pago")
public class MercadoPagoWebhookController {

    private final ProductOrderService orderService;

    @PostMapping("/mercadopago")
    @Operation(summary = "Webhook Mercado Pago (fuente de verdad del pago)")
    public ResponseEntity<WebhookAckResponse> mercadopago(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String dataIdQuery,
            @RequestParam(value = "type", required = false) String typeQuery,
            @RequestBody(required = false) JsonNode body
    ) {
        String dataId = firstNonBlank(dataIdQuery, extractDataId(body));
        String type = firstNonBlank(typeQuery, text(body, "type"));
        if (dataId == null || dataId.isBlank()) {
            return ResponseEntity.ok(WebhookAckResponse.builder()
                    .received(true)
                    .applied(false)
                    .build());
        }
        return ResponseEntity.ok(orderService.handleMercadoPagoWebhook(
                dataId, xRequestId, xSignature, type));
    }

    private static String extractDataId(JsonNode body) {
        if (body == null) {
            return null;
        }
        if (body.has("data") && body.get("data").hasNonNull("id")) {
            return body.get("data").get("id").asText();
        }
        if (body.hasNonNull("id") && body.path("type").asText("").equalsIgnoreCase("payment")) {
            return body.get("id").asText();
        }
        return null;
    }

    private static String text(JsonNode body, String field) {
        if (body == null || !body.hasNonNull(field)) {
            return null;
        }
        return body.get(field).asText();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
