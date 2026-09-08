package com.metrix.api.platform.service;

import com.metrix.api.dto.productos.SimulatedPaymentRequest;
import com.metrix.api.platform.model.PaymentProvider;
import com.metrix.api.platform.model.ProductOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pasarela local: checkout inventa una preferencia SIM-*; charge acepta tarjetas
 * válidas excepto las que terminan en {@code 0000}.
 */
@Service
@ConditionalOnProperty(name = "metrix.payments.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.SIMULATED;
    }

    @Override
    public boolean supportsSimulatedCardCharge() {
        return true;
    }

    @Override
    public CheckoutSession createCheckout(ProductOrder order, CheckoutUrls urls) {
        String preferenceId = order.getPreferenceId();
        if (preferenceId == null || preferenceId.isBlank()) {
            preferenceId = "SIM-PREF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        // Local: redirige al formulario simulado /productos/pago/:orderId
        String initPoint = urls.successUrl()
                .replace("/productos/pago-retorno/", "/productos/pago/")
                .replaceAll("\\?.*$", "");
        return new CheckoutSession(preferenceId, initPoint, initPoint);
    }

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
