package com.metrix.api.platform.service;

import com.metrix.api.dto.productos.SimulatedPaymentRequest;

import java.math.BigDecimal;

/**
 * Abstracción de pasarela de pago. v1: implementación simulada.
 */
public interface PaymentGateway {

    PaymentResult charge(BigDecimal amount, String currency, SimulatedPaymentRequest request);

    record PaymentResult(boolean success, String reference, String message) {
    }
}
