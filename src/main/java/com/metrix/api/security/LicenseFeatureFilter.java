package com.metrix.api.security;

import com.metrix.api.platform.license.LicenseFeatureCodes;
import com.metrix.api.platform.service.TenantLicenseGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 403 en APIs de módulos no incluidos en el plan del tenant.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class LicenseFeatureFilter extends OncePerRequestFilter {

    private static final Map<String, String> PATH_PREFIX_TO_FEATURE = new LinkedHashMap<>();

    static {
        PATH_PREFIX_TO_FEATURE.put("/api/v1/trainings", LicenseFeatureCodes.TRAININGS);
        PATH_PREFIX_TO_FEATURE.put("/api/v1/training-templates", LicenseFeatureCodes.TRAININGS);
        PATH_PREFIX_TO_FEATURE.put("/api/v1/training-materials", LicenseFeatureCodes.TRAININGS);
        PATH_PREFIX_TO_FEATURE.put("/api/v1/exams", LicenseFeatureCodes.EXAMS);
        PATH_PREFIX_TO_FEATURE.put("/api/v1/exam-templates", LicenseFeatureCodes.EXAMS);
        PATH_PREFIX_TO_FEATURE.put("/api/v1/bank-questions", LicenseFeatureCodes.EXAMS);
        PATH_PREFIX_TO_FEATURE.put("/api/v1/question-bank", LicenseFeatureCodes.EXAMS);
        PATH_PREFIX_TO_FEATURE.put("/api/v1/gamification", LicenseFeatureCodes.GAMIFICATION);
    }

    private final TenantLicenseGuard tenantLicenseGuard;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String feature = resolveFeature(request.getRequestURI());
        if (feature != null) {
            try {
                tenantLicenseGuard.assertFeature(feature);
            } catch (AccessDeniedException ex) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                String msg = ex.getMessage() != null ? ex.getMessage() : "Módulo no incluido en tu plan";
                response.getWriter().write("{\"error\":\"" + msg.replace("\"", "'") + "\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    static String resolveFeature(String uri) {
        if (uri == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : PATH_PREFIX_TO_FEATURE.entrySet()) {
            String prefix = entry.getKey();
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                return entry.getValue();
            }
        }
        return null;
    }
}
