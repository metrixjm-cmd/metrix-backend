package com.metrix.api.dto.productos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SimulatedPaymentRequest {

    /** Referencia simulada de la pasarela (opcional). */
    private String paymentReference;

    @NotBlank
    private String cardholderName;

    @NotBlank
    @Size(min = 13, max = 19)
    private String cardNumber;

    @NotBlank
    private String expiryMonth;

    @NotBlank
    private String expiryYear;

    @NotBlank
    @Size(min = 3, max = 4)
    private String cvv;
}
