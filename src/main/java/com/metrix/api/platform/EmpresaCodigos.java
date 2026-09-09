package com.metrix.api.platform;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Código público de plataforma ({@code TACOS-A3F2}) que discrimina el login
 * entre tenants. Admin 0 usa {@link #PLATFORM}.
 */
public final class EmpresaCodigos {

    public static final String PLATFORM = "METRIX";
    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 24;

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9-]{3,24}$");

    private EmpresaCodigos() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isBlank(String codigo) {
        return normalize(codigo).isEmpty();
    }

    public static boolean isPlatform(String codigo) {
        return PLATFORM.equals(normalize(codigo));
    }

    public static boolean isValidFormat(String codigo) {
        String normalized = normalize(codigo);
        if (normalized.isEmpty()) {
            return true;
        }
        return VALID.matcher(normalized).matches();
    }

    /**
     * Slug corto + 4 hex del id de instancia. Nunca genera {@link #PLATFORM}.
     */
    public static String generate(String empresaNombre, String instanceId) {
        String slug = slug(empresaNombre);
        String suffix = hexSuffix(instanceId, 4);
        String code = slug + "-" + suffix;
        if (PLATFORM.equals(code) || code.length() > MAX_LENGTH) {
            code = "CLIENTE-" + suffix;
        }
        if (code.length() > MAX_LENGTH) {
            code = code.substring(0, MAX_LENGTH);
        }
        if (PLATFORM.equals(code) || code.length() < MIN_LENGTH) {
            code = "T-" + suffix;
        }
        return code;
    }

    public static String withExtraSuffix(String base, String extraHex) {
        String suffix = hexSuffix(extraHex, 4);
        String candidate = normalize(base);
        if (candidate.length() + 1 + suffix.length() > MAX_LENGTH) {
            candidate = candidate.substring(0, MAX_LENGTH - 1 - suffix.length());
        }
        candidate = candidate.replaceAll("-+$", "") + "-" + suffix;
        if (PLATFORM.equals(candidate)) {
            candidate = "T-" + suffix;
        }
        return candidate;
    }

    static String slug(String empresaNombre) {
        String slug = Normalizer.normalize(empresaNombre == null ? "" : empresaNombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            slug = "CLIENTE";
        }
        if (slug.length() > 8) {
            slug = slug.substring(0, 8);
        }
        if (PLATFORM.equals(slug)) {
            slug = "CLIENTE";
        }
        return slug;
    }

    private static String hexSuffix(String instanceId, int length) {
        String hex = (instanceId == null ? "" : instanceId).replace("-", "").toLowerCase(Locale.ROOT);
        if (hex.length() < length) {
            hex = (hex + "00000000");
        }
        return hex.substring(0, length).toUpperCase(Locale.ROOT);
    }
}
