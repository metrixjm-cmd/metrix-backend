package com.metrix.api.exception;

/**
 * Excepción lanzada cuando un recurso solicitado no existe en la base de datos.
 * Resulta en una respuesta HTTP 404 Not Found (ver {@link GlobalExceptionHandler}).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
