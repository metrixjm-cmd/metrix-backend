package com.metrix.api.security;

import com.metrix.api.platform.license.LicenseFeatureCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LicenseFeatureFilterTest {

    @Test
    void mapsModulePaths() {
        assertEquals(LicenseFeatureCodes.TRAININGS, LicenseFeatureFilter.resolveFeature("/api/v1/trainings"));
        assertEquals(LicenseFeatureCodes.TRAININGS, LicenseFeatureFilter.resolveFeature("/api/v1/trainings/my"));
        assertEquals(LicenseFeatureCodes.EXAMS, LicenseFeatureFilter.resolveFeature("/api/v1/exams/store/1"));
        assertEquals(LicenseFeatureCodes.EXAMS, LicenseFeatureFilter.resolveFeature("/api/v1/question-bank"));
        assertEquals(LicenseFeatureCodes.GAMIFICATION, LicenseFeatureFilter.resolveFeature("/api/v1/gamification/me"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/users"));
        assertNull(LicenseFeatureFilter.resolveFeature("/api/v1/trainings-extra"));
    }
}
