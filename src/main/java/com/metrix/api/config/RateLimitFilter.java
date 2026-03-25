package com.metrix.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter in-memory por instancia (sin Redis).
 * <p>
 * Decisión de arquitectura: Con Cloud Run max-instances=1, un rate limiter
 * local cubre el 100% del tráfico. Ahorro: ~$25-50/mes de Memorystore.
 * <p>
 * Usa ventana fija de 1 minuto con contadores atómicos.
 * Se limpia automáticamente cada minuto para evitar memory leak.
 * <p>
 * Límites:
 * <ul>
 *   <li>GET/HEAD: 200 req/min por cliente</li>
 *   <li>POST/PATCH/PUT/DELETE: 50 req/min por cliente</li>
 * </ul>
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int READ_LIMIT  = 200;
    private static final int WRITE_LIMIT = 50;

    // clientId → counter (se limpia cada minuto)
    private final Map<String, AtomicInteger> readBuckets  = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> writeBuckets = new ConcurrentHashMap<>();
    private volatile long currentWindow = System.currentTimeMillis() / 60_000;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if ("/health".equals(path) || "/actuator/health".equals(path)
                || "OPTIONS".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Rotar ventana si pasó el minuto
        long now = System.currentTimeMillis() / 60_000;
        if (now != currentWindow) {
            currentWindow = now;
            readBuckets.clear();
            writeBuckets.clear();
        }

        String clientId = resolveClientId(request);
        boolean isWrite = !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod());
        int limit = isWrite ? WRITE_LIMIT : READ_LIMIT;
        Map<String, AtomicInteger> buckets = isWrite ? writeBuckets : readBuckets;

        int count = buckets.computeIfAbsent(clientId, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (count > limit) {
            log.warn("[RateLimit] {} exceeded: {} req/min (limit {})", clientId, count, limit);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too many requests\",\"retryAfterSeconds\":60}");
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));

        filterChain.doFilter(request, response);
    }

    private String resolveClientId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return "u:" + Integer.toHexString(auth.hashCode());
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return "ip:" + ip.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
