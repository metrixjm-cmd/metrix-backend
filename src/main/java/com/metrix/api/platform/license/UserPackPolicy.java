package com.metrix.api.platform.license;

import com.metrix.api.platform.model.MetrixInstance;

import java.time.Duration;
import java.time.Instant;

/**
 * Un solo paquete extra de usuarios por licencia. Cada pago cubre {@link #PERIOD}.
 */
public final class UserPackPolicy {

    public static final Duration PERIOD = Duration.ofDays(30);
    public static final String REF_PREFIX = "upk:";
    public static final int DEFAULT_PACK_SIZE = 10;

    private UserPackPolicy() {}

    public static int activeExtra(MetrixInstance instance, Instant now) {
        if (instance == null || instance.getExtraUsuarios() <= 0) {
            return 0;
        }
        Instant until = instance.getExtraUsuariosHasta();
        if (until == null || !until.isAfter(now)) {
            return 0;
        }
        return instance.getExtraUsuarios();
    }

    public static boolean expired(MetrixInstance instance, Instant now) {
        return instance != null
                && instance.getExtraUsuarios() > 0
                && activeExtra(instance, now) == 0;
    }

    public static String externalReference(String orderId) {
        return REF_PREFIX + orderId;
    }

    /** {@code null} si la referencia no es de un paquete de usuarios. */
    public static String orderIdFromReference(String externalReference) {
        if (externalReference == null || !externalReference.startsWith(REF_PREFIX)) {
            return null;
        }
        String id = externalReference.substring(REF_PREFIX.length());
        return id.isBlank() ? null : id;
    }
}
