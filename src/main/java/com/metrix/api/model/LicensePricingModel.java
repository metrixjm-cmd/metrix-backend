package com.metrix.api.model;

/**
 * Modelo de cobro del paquete comercial.
 */
public enum LicensePricingModel {
    /** Precio fijo por sucursal al mes (METRIX Base). */
    PER_BRANCH,
    /** Precio fijo mensual del plan (METRIX Pro). */
    FLAT_MONTHLY,
    /** Precio por usuario al mes (Expansión, Prime). */
    PER_USER
}
