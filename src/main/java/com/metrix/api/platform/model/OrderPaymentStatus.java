package com.metrix.api.platform.model;

/**
 * Estado del cobro en la pasarela (distinto de {@link ProductOrderStatus}).
 */
public enum OrderPaymentStatus {
    NONE,
    PENDING,
    APPROVED,
    REJECTED
}
