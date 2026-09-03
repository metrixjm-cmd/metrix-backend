package com.metrix.api.security;

import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.service.PlatformAdminService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bloquea requests de un tenant cuya instancia está SUSPENDED.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
@RequiredArgsConstructor
public class SuspendedInstanceFilter extends OncePerRequestFilter {

    private final PlatformAdminService platformAdminService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!TenantContext.isPlatformAdmin()) {
            String instanceId = TenantContext.getInstanceId();
            if (instanceId != null && !instanceId.isBlank() && platformAdminService.isSuspended(instanceId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                String message = platformAdminService.suspensionMessage(instanceId);
                response.getWriter().write("{\"error\":\"" + jsonEscape(message) + "\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
