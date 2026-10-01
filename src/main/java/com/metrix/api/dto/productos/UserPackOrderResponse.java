package com.metrix.api.dto.productos;

import com.metrix.api.platform.model.OrderPaymentStatus;
import com.metrix.api.platform.model.PaymentProvider;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class UserPackOrderResponse {

    private String orderId;
    private int usuarios;
    private BigDecimal monto;
    private String moneda;
    private OrderPaymentStatus paymentStatus;
    private PaymentProvider paymentProvider;
    private String preferenceId;
    private Instant paidAt;
    private Instant periodoInicio;
    private Instant periodoFin;
}
