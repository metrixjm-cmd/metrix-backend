package com.metrix.api.platform.license;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Códigos estables de módulos licenciables (no etiquetas de marketing).
 */
public final class LicenseFeatureCodes {

    public static final String TRAININGS = "TRAININGS";
    public static final String EXAMS = "EXAMS";
    public static final String GAMIFICATION = "GAMIFICATION";
    public static final String API = "API";

    public static final List<String> ALL_MODULES = List.of(TRAININGS, EXAMS, GAMIFICATION, API);

    private LicenseFeatureCodes() {
    }

    /**
     * Fallback para órdenes/paquetes antiguos sin {@code featureCodes} en snapshot.
     */
    public static List<String> defaultsForPackageId(String packageId) {
        if (packageId == null || packageId.isBlank()) {
            return List.of();
        }
        return switch (packageId.trim().toLowerCase(Locale.ROOT)) {
            case "base" -> List.of();
            case "pro", "expansion" -> List.of(TRAININGS, EXAMS, GAMIFICATION);
            case "prime" -> List.of(TRAININGS, EXAMS, GAMIFICATION, API);
            default -> List.of();
        };
    }

    public static boolean includes(List<String> codes, String required) {
        if (required == null || required.isBlank()) {
            return true;
        }
        if (codes == null || codes.isEmpty()) {
            return false;
        }
        String needle = required.trim().toUpperCase(Locale.ROOT);
        return codes.stream().anyMatch(c -> c != null && c.trim().equalsIgnoreCase(needle));
    }

    public static Set<String> asSet(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Set.of();
        }
        return codes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
