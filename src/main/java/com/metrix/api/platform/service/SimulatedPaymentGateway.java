package com.metrix.api.platform.service;

import com.metrix.api.dto.productos.SimulatedPaymentRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pasarela simulada: acepta cualquier tarjeta con formato válido.
 * Tarjetas que terminan en {@code 0000} simulan rechazo para pruebas.
 */
@Service
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(BigDecimal amount, String currency, SimulatedPaymentRequest request) {
        String digits = request.getCardNumber().replaceAll("\\D", "");
        if (digits.endsWith("0000")) {
            return new PaymentResult(false, null, "Pago rechazado por el banco emisor (simulado).");
        }
        String reference = "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PaymentResult(true, reference,
                "Pago simulado aprobado por " + amount + " " + currency);
    }
}
