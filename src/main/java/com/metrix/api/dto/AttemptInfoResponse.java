package com.metrix.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Información de intentos del usuario actual sobre un examen específico.
 * Usada por el frontend para bloquear la pantalla de "Tomar examen" si se agotaron los intentos.
 */
@Data
@Builder
public class AttemptInfoResponse {

    /** Número de intentos ya realizados por este usuario en este examen. */
    private long attemptCount;

    /** Máximo de intentos permitidos. 0 = ilimitado. */
    private int maxAttempts;

    /** true si el usuario puede intentar el examen (maxAttempts=0 o attemptCount < maxAttempts). */
    private boolean canAttempt;

    /** Intentos restantes. -1 si es ilimitado. */
    private long remainingAttempts;
}
