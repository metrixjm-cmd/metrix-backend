package com.metrix.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// Regression: ISSUE-001 — rutas no mapeadas (p.ej. /swagger-ui.html con
// springdoc desactivado en prod) devolvían 500 con detalles internos
// ("No static resource ...") en vez de 404.
// Found by /qa on 2026-07-03
// Report: .gstack/qa-reports/qa-report-metrix-backend-2026-07-03.md
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void no_resource_found_returns_404_without_internal_details() {
        var response = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "swagger-ui.html"));

        assertEquals(404, response.getStatusCode().value());
        assertEquals("Recurso no encontrado", response.getBody().get("error"));
        assertFalse(response.getBody().get("error").toString().contains("static resource"),
                "El mensaje no debe filtrar detalles internos de Spring");
    }

    @Test
    void unsupported_http_method_returns_405() {
        var response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PATCH"));

        assertEquals(405, response.getStatusCode().value());
        assertEquals("Método HTTP no soportado para esta ruta", response.getBody().get("error"));
    }

    @Test
    void generic_exception_still_returns_500() {
        var response = handler.handleGeneral(new RuntimeException("boom"));

        assertEquals(500, response.getStatusCode().value());
    }
}
