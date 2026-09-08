package com.metrix.api.platform.service;

import com.metrix.api.dto.productos.SimulatedPaymentRequest;
import com.metrix.api.platform.model.PaymentProvider;
import com.metrix.api.platform.model.ProductOrder;

import java.math.BigDecimal;

/**
 * Abstracción de pasarela. Checkout Pro crea preferencia; el cobro real llega
 * por webhook. {@link #charge} solo aplica al proveedor simulado (local/test).
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /**
     * Crea (o reutiliza) una sesión de checkout. No marca la orden como pagada.
     */
    CheckoutSession createCheckout(ProductOrder order, CheckoutUrls urls);

    /**
     * Cobro síncrono con tarjeta. Solo {@link PaymentProvider#SIMULATED}.
     */
    default PaymentResult charge(BigDecimal amount, String currency, SimulatedPaymentRequest request) {
        throw new UnsupportedOperationException(
                "El cobro con tarjeta solo está disponible con la pasarela simulada (local).");
    }

    default boolean supportsSimulatedCardCharge() {
        return false;
    }

    record CheckoutUrls(String successUrl, String failureUrl, String pendingUrl, String notificationUrl) {
    }

    record CheckoutSession(
            String preferenceId,
            String initPoint,
            String sandboxInitPoint
    ) {
    }

    record PaymentResult(boolean success, String reference, String message) {
    }
}
