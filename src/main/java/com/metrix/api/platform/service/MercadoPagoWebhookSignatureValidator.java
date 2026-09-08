package com.metrix.api.platform.service;

import com.metrix.api.platform.config.MercadoPagoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Valida {@code x-signature} de webhooks Mercado Pago (manifest id + request-id + ts).
 */
@Component
@RequiredArgsConstructor
public class MercadoPagoWebhookSignatureValidator {

    private final MercadoPagoProperties properties;

    public boolean isValid(String dataId, String xRequestId, String xSignature) {
        String secret = properties.getMercadopago().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            // Sin secreto configurado no aceptamos webhooks (evita open relay en misconfig).
            return false;
        }
        secret = secret.trim();
        if (xSignature == null || xSignature.isBlank() || dataId == null || dataId.isBlank()) {
            return false;
        }

        Map<String, String> parts = parseSignature(xSignature);
        String ts = parts.get("ts");
        String v1 = parts.get("v1");
        if (ts == null || v1 == null) {
            return false;
        }

        // MP: ids alfanuméricos van en minúsculas; numéricos quedan igual.
        String normalizedId = dataId.trim().toLowerCase(Locale.ROOT);

        // Probar variantes: docs indican omitir request-id si no viene.
        // Algunos envíos traen el header pero firman sin él (o al revés).
        if (matches(secret, normalizedId, xRequestId, ts, v1)) {
            return true;
        }
        if (xRequestId != null && !xRequestId.isBlank() && matches(secret, normalizedId, null, ts, v1)) {
            return true;
        }
        // Fallback: id sin normalizar (por si el id numérico se alteró)
        if (!normalizedId.equals(dataId.trim()) && matches(secret, dataId.trim(), xRequestId, ts, v1)) {
            return true;
        }
        return xRequestId != null && !xRequestId.isBlank()
                && !normalizedId.equals(dataId.trim())
                && matches(secret, dataId.trim(), null, ts, v1);
    }

    private static boolean matches(
            String secret, String dataId, String xRequestId, String ts, String v1) {
        StringBuilder manifest = new StringBuilder();
        manifest.append("id:").append(dataId).append(";");
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifest.append("request-id:").append(xRequestId).append(";");
        }
        manifest.append("ts:").append(ts).append(";");

        String expected = hmacSha256Hex(secret, manifest.toString());
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> parseSignature(String raw) {
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        for (String part : raw.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim().toLowerCase(Locale.ROOT), kv[1].trim());
            }
        }
        return map;
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular HMAC de webhook MP", e);
        }
    }
}
