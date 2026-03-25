package com.metrix.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persona involucrada en una incidencia operativa.
 * <p>
 * Documento embebido en {@link Incident#implicados}.
 * tipo puede ser: GERENTE, EJECUTADOR, EXTERNO.
 * Si tipo = EXTERNO, el campo responsabilidad describe su rol
 * (ej. "Proveedor de gas", "Servicio de mantenimiento").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImplicadoEntry {

    /** Nombre completo o identificador de la persona. */
    private String nombre;

    /** Rol en la organización: GERENTE | EJECUTADOR | EXTERNO */
    private String tipo;

    /**
     * Solo aplica cuando tipo = EXTERNO.
     * Describe la responsabilidad o relación con la operación
     * (ej. "Proveedor de gas LP", "Empresa de limpieza").
     */
    private String responsabilidad;
}
