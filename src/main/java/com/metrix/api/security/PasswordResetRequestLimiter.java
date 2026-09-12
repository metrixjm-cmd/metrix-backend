package com.metrix.api.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita solicitudes públicas de reset por par {@code codigoEmpresa + #Usuario}.
 * Cuenta todos los intentos (exista o no la cuenta) para no filtrar existencia.
 */
@Slf4j
@Component
public class PasswordResetRequestLimiter {

    static final int MAX_ATTEMPTS = 3;
    static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final Duration STALE_AFTER = Duration.ofHours(1);

    private record Attempts(int count, Instant last) {}

    private final Map<String, Attempts> byUser = new ConcurrentHashMap<>();

    public boolean isBlocked(String codigoEmpresa, String numeroUsuario) {
        Attempts a = byUser.get(key(codigoEmpresa, numeroUsuario));
        if (a == null || a.count() < MAX_ATTEMPTS) return false;
        if (Instant.now().isAfter(a.last().plus(LOCKOUT))) {
            byUser.remove(key(codigoEmpresa, numeroUsuario));
            return false;
        }
        return true;
    }

    public void recordAttempt(String codigoEmpresa, String numeroUsuario) {
        purgeStale();
        byUser.merge(
                key(codigoEmpresa, numeroUsuario),
                new Attempts(1, Instant.now()),
                (prev, fresh) -> new Attempts(prev.count() + 1, fresh.last()));
    }

    private void purgeStale() {
        Instant cutoff = Instant.now().minus(STALE_AFTER);
        byUser.entrySet().removeIf(e -> e.getValue().last().isBefore(cutoff));
    }

    private String key(String codigoEmpresa, String numeroUsuario) {
        String user = numeroUsuario == null ? "" : numeroUsuario.trim().toUpperCase();
        String code = codigoEmpresa == null ? "" : codigoEmpresa.trim().toUpperCase();
        return code + "|" + user;
    }
}
