package com.metrix.api.platform.service;

import com.metrix.api.platform.config.MercadoPagoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MercadoPagoWebhookSignatureValidatorTest {

    private MercadoPagoWebhookSignatureValidator validator;

    @BeforeEach
    void setUp() {
        MercadoPagoProperties props = new MercadoPagoProperties();
        props.getMercadopago().setWebhookSecret("test-secret");
        validator = new MercadoPagoWebhookSignatureValidator(props);
    }

    @Test
    void acceptsValidSignature() throws Exception {
        String dataId = "999";
        String requestId = "abc-req";
        String ts = "1700000000";
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        String v1 = hmac(manifest);
        String signature = "ts=" + ts + ",v1=" + v1;

        assertTrue(validator.isValid(dataId, requestId, signature));
    }

    @Test
    void rejectsTamperedSignature() {
        assertFalse(validator.isValid("999", "abc-req", "ts=1,v1=deadbeef"));
    }

    @Test
    void acceptsValidSignatureWithoutRequestId() throws Exception {
        String dataId = "999";
        String ts = "1700000000";
        String manifest = "id:" + dataId + ";ts:" + ts + ";";
        String v1 = hmac(manifest);
        String signature = "ts=" + ts + ",v1=" + v1;

        assertTrue(validator.isValid(dataId, null, signature));
        // Si llega request-id pero la firma no lo incluye, también aceptar
        assertTrue(validator.isValid(dataId, "abc-req", signature));
    }

    @Test
    void acceptsAlphanumericIdLowercased() throws Exception {
        String dataId = "ORD01ABC";
        String requestId = "abc-req";
        String ts = "1700000000";
        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + ts + ";";
        String v1 = hmac(manifest);
        String signature = "ts=" + ts + ",v1=" + v1;

        assertTrue(validator.isValid(dataId, requestId, signature));
    }

    private static String hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
