package com.metrix.api.exception;

/**
 * Se lanza cuando una cuenta acumula demasiados intentos de login fallidos.
 * Se traduce a HTTP 429 en {@code GlobalExceptionHandler}.
 */
public class TooManyLoginAttemptsException extends RuntimeException {
    public TooManyLoginAttemptsException(String message) {
        super(message);
    }
}
