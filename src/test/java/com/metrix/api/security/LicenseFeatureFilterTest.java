package com.metrix.api.security;

import com.metrix.api.platform.license.LicenseFeatureCodes;
import com.metrix.api.platform.service.TenantLicenseGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contrato Fase 1 Banco de Datos: premium mapeado; núcleo sin feature.
 */
@ExtendWith(MockitoExtension.class)
class LicenseFeatureFilterTest {

    @Mock
    private TenantLicenseGuard tenantLicenseGuard;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private LicenseFeatureFilter filter;

    @Test
    void mapsTrainingsPaths() {
        assertEquals(LicenseFeatureCodes.TRAININGS, LicenseFeatureFilter.resolveFeature("/api/v1/trainings"));
        assertEquals(LicenseFeatureCodes.TRAININGS, LicenseFeatureFilter.resolveFeature("/api/v1/trainings/my"));
        assertEquals(LicenseFeatureCodes.TRAININGS, LicenseFeatureFilter.resolveFeature("/api/v1/training-templates"));
        assertEquals(LicenseFeatureCodes.TRAININGS, LicenseFeatureFilter.resolveFeature("/api/v1/training-templates/x"));
        assertEquals(LicenseFeatureCodes.TRAININGS, LicenseFeatureFilter.resolveFeature("/api/v1/training-materials"));
        assertEquals(LicenseFeatureCodes.TRAININGS, LicenseFeatureFilter.resolveFeature("/api/v1/training-materials/1"));
    }

    @Test
    void mapsExamsPaths() {
        assertEquals(LicenseFeatureCodes.EXAMS, LicenseFeatureFilter.resolveFeature("/api/v1/exams"));
        assertEquals(LicenseFeatureCodes.EXAMS, LicenseFeatureFilter.resolveFeature("/api/v1/exams/store/1"));
        assertEquals(LicenseFeatureCodes.EXAMS, LicenseFeatureFilter.resolveFeature("/api/v1/exam-templates"));
        assertEquals(LicenseFeatureCodes.EXAMS, LicenseFeatureFilter.resolveFeature("/api/v1/exam-templates/summaries"));
        assertEquals(LicenseFeatureCodes.EXAMS, LicenseFeatureFilter.resolveFeature("/api/v1/question-bank"));
        assertEquals(LicenseFeatureCodes.EXAMS, LicenseFeatureFilter.resolveFeature("/api/v1/question-bank/tags"));
        assertEquals(LicenseFeatureCodes.EXAMS, LicenseFeatureFilter.resolveFeature("/api/v1/bank-questions"));
    }

    @Test
    void mapsGamificationPaths() {
        assertEquals(LicenseFeatureCodes.GAMIFICATION, LicenseFeatureFilter.resolveFeature("/api/v1/gamification"));
        assertEquals(LicenseFeatureCodes.GAMIFICATION, LicenseFeatureFilter.resolveFeature("/api/v1/gamification/me"));
    }

    @Test
    void bancoCorePathsHaveNoLicenseFeature() {
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/users"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/users/all"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/stores"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/stores/next-code"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/catalogs/PUESTO"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/catalogs/TURNO"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/task-templates"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/task-templates/1"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/categorias"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/tasks"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/incidents"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/trainings-extra"));
    }

    @Test
    void doFilter_skipsAssertOnCorePath() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        filter.doFilter(request, response, filterChain);
        verify(tenantLicenseGuard, never()).assertFeature(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_returns403JsonWhenFeatureMissing() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/question-bank");
        doThrow(new AccessDeniedException("Tu plan no incluye el módulo EXAMS. Actualiza tu licencia."))
                .when(tenantLicenseGuard).assertFeature(LicenseFeatureCodes.EXAMS);

        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertTrue(body.toString().contains("\"error\""));
        assertTrue(body.toString().contains("EXAMS"));
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_allowsPremiumWhenGuardPasses() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/training-templates");
        filter.doFilter(request, response, filterChain);
        verify(tenantLicenseGuard).assertFeature(LicenseFeatureCodes.TRAININGS);
        verify(filterChain).doFilter(request, response);
    }
}
