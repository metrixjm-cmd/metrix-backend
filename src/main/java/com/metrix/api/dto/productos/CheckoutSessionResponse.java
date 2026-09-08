package com.metrix.api.dto.productos;

import com.metrix.api.platform.model.OrderPaymentStatus;
import com.metrix.api.platform.model.PaymentProvider;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CheckoutSessionResponse {

    private String orderId;
    private String preferenceId;
    private String initPoint;
    private String sandboxInitPoint;
    private String status;
    private BigDecimal totalCobrado;
    private String moneda;
    private PaymentProvider paymentProvider;
    private OrderPaymentStatus paymentStatus;
}
