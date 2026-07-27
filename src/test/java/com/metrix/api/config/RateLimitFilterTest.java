package com.metrix.api.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        chain = mock(FilterChain.class);
    }

    /**
     * La regresión: el límite se saltaba rotando {@code X-Forwarded-For}.
     * <p>
     * La cabecera se va agregando por la derecha en cada salto, así que el primer
     * elemento es texto que manda el cliente. Leyéndolo, cada petición estrenaba
     * bucket y el límite no llegaba a aplicarse nunca — comprobado contra
     * {@code /auth/login}: 120 de 120 intentos pasaron.
     */
    @Test
    void rotarXForwardedForNoEstrenaBucket() throws Exception {
        int bloqueadas = 0;
        for (int i = 0; i < 60; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            // Prefijo distinto en cada intento; la IP real la añade la
            // infraestructura al final y es la que debe contar.
            filter.doFilter(login("10.0.0." + i + ", 203.0.113.7"), response, chain);
            if (response.getStatus() == 429) bloqueadas++;
        }

        assertEquals(10, bloqueadas,
                     "50 pasan y el resto se bloquea: el prefijo falseado no debe abrir bucket nuevo");
    }

    @Test
    void clientesDistintosNoCompartenBucket() throws Exception {
        for (int i = 0; i < 50; i++) {
            filter.doFilter(login("203.0.113.7"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse otro = new MockHttpServletResponse();
        filter.doFilter(login("198.51.100.4"), otro, chain);

        assertEquals(200, otro.getStatus(),
                     "agotar el cupo de una IP no puede afectar a otra");
    }

    @Test
    void sinCabeceraCaeEnLaIpDelPeer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.0.2.55");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertEquals("50", response.getHeader("X-RateLimit-Limit"));
        assertEquals("49", response.getHeader("X-RateLimit-Remaining"));
    }

    private MockHttpServletRequest login(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", forwardedFor);
        request.setRemoteAddr("10.255.255.1"); // el proxy, no el cliente
        return request;
    }
}
