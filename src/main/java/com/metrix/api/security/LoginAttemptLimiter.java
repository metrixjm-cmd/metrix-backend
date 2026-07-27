package com.metrix.api.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Frena la fuerza bruta de credenciales contando fallos <b>por número de usuario</b>.
 * <p>
 * Complementa a {@code RateLimitFilter}, que cuenta por cliente. Las dos capas hacen
 * falta: el filtro cuenta por IP, y una IP es barata — un atacante con varias
 * direcciones reparte los intentos y ninguna llega al límite, aunque todos vayan
 * contra la misma cuenta. Este contador va por cuenta, así que repartir no ayuda.
 * <p>
 * In-memory, coherente con el resto del backend (Cloud Run {@code max-instances=1},
 * ver {@code deploy.yml}). Si el servicio escalara, cada instancia contaría por su
 * lado y el umbral efectivo se multiplicaría por el número de instancias.
 * <p>
 * El bloqueo es temporal y por intento, no un cierre de cuenta: bloquear la cuenta
 * de forma persistente convertiría esto en una herramienta de denegación de servicio
 * contra usuarios legítimos, a los que cualquiera podría dejar fuera a propósito.
 */
@Slf4j
@Component
public class LoginAttemptLimiter {

    /** Fallos consecutivos tolerados antes de bloquear. */
    static final int MAX_FAILURES = 10;

    /** Cuánto dura el bloqueo desde el último fallo. */
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    /** Tras este tiempo sin fallar, el contador se descarta. */
    private static final Duration STALE_AFTER = Duration.ofHours(1);

    private record Attempts(int failures, Instant lastFailure) {}

    private final Map<String, Attempts> byUser = new ConcurrentHashMap<>();

    /**
     * @return true si la cuenta está en periodo de bloqueo y no debe intentarse
     *         autenticar
     */
    public boolean isBlocked(String numeroUsuario) {
        Attempts a = byUser.get(key(numeroUsuario));
        if (a == null || a.failures() < MAX_FAILURES) return false;

        if (Instant.now().isAfter(a.lastFailure().plus(LOCKOUT))) {
            byUser.remove(key(numeroUsuario));
            return false;
        }
        return true;
    }

    public void recordFailure(String numeroUsuario) {
        purgeStale();
        byUser.merge(
                key(numeroUsuario),
                new Attempts(1, Instant.now()),
                (prev, fresh) -> new Attempts(prev.failures() + 1, fresh.lastFailure()));

        Attempts now = byUser.get(key(numeroUsuario));
        if (now != null && now.failures() == MAX_FAILURES) {
            log.warn("[LoginAttempt] cuenta bloqueada {} min tras {} fallos: {}",
                     LOCKOUT.toMinutes(), MAX_FAILURES, key(numeroUsuario));
        }
    }

    public void recordSuccess(String numeroUsuario) {
        byUser.remove(key(numeroUsuario));
    }

    /**
     * El mapa sólo crece con cuentas que fallan, pero un atacante puede inventar
     * usuarios inexistentes indefinidamente; sin limpieza sería una fuga de memoria.
     */
    private void purgeStale() {
        Instant cutoff = Instant.now().minus(STALE_AFTER);
        byUser.entrySet().removeIf(e -> e.getValue().lastFailure().isBefore(cutoff));
    }

    /** Normaliza para que variar mayúsculas no estrene contador. */
    private String key(String numeroUsuario) {
        return numeroUsuario == null ? "" : numeroUsuario.trim().toUpperCase();
    }
}
