package com.metrix.api.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "metrix.payments")
public class MercadoPagoProperties {

    /**
     * {@code simulated} (default local) o {@code mercadopago}.
     */
    private String provider = "simulated";

    /** Base pública del API (Cloud Run) para notification_url. */
    private String publicApiUrl = "http://localhost:8080";

    /** Base del SPA para back_urls. */
    private String frontendBaseUrl = "http://localhost:4200";

    private final MercadoPago mercadopago = new MercadoPago();

    @Data
    public static class MercadoPago {
        private String accessToken = "";
        private String webhookSecret = "";
        private String apiBaseUrl = "https://api.mercadopago.com";
    }

    public boolean isMercadoPago() {
        return "mercadopago".equalsIgnoreCase(provider);
    }
}
