package com.metrix.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuración de beans de infraestructura general para METRIX.
 * <p>
 * Centraliza la creación de clientes HTTP y otros beans de soporte
 * que no pertenecen al dominio de seguridad ({@code SecurityConfig}).
 */
@Configuration
public class AppConfig {

    /**
     * Bean singleton de {@link RestTemplate} para comunicación síncrona
     * con el analytics-service Python (Sprint 17).
     * <p>
     * Spring Boot auto-configura los {@code HttpMessageConverter} de Jackson
     * cuando detecta Jackson en el classpath, por lo que la deserialización
     * de JSON a {@code IgeoAnalyticsResponse} (record Java 21) funciona sin
     * configuración adicional.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
