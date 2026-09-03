package com.metrix.api.platform.license;

/**
 * Días de prueba por paquete. {@code null} usa el default; {@code 0} desactiva la prueba.
 */
public final class LicenseTrialDays {

    public static final int DEFAULT = 7;

    private LicenseTrialDays() {}

    public static int resolve(Integer diasPrueba) {
        if (diasPrueba == null || diasPrueba < 0) {
            return DEFAULT;
        }
        return diasPrueba;
    }
}
