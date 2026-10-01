package com.metrix.api.controller;

import com.metrix.api.dto.productos.CheckoutSessionResponse;
import com.metrix.api.dto.productos.SimulatedPaymentRequest;
import com.metrix.api.dto.productos.UserPackOrderResponse;
import com.metrix.api.dto.productos.UserPackStatusResponse;
import com.metrix.api.platform.service.UserPackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/license/user-pack")
@RequiredArgsConstructor
@Tag(name = "Paquete de usuarios", description = "Un paquete adicional de usuarios por licencia, renovable cada 30 días")
public class UserPackController {

    private final UserPackService userPackService;

    @GetMapping
    @Operation(summary = "Estado del paquete adicional de la licencia actual")
    public UserPackStatusResponse status() {
        return userPackService.status();
    }

    @PostMapping("/checkout")
    @Operation(summary = "Crear cobro del paquete (no marca pagado)")
    public ResponseEntity<CheckoutSessionResponse> checkout() {
        return ResponseEntity.status(HttpStatus.CREATED).body(userPackService.checkout());
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Consultar un cobro del paquete de esta licencia")
    public UserPackOrderResponse getOrder(@PathVariable String orderId) {
        return userPackService.getOrder(orderId);
    }

    @PostMapping("/orders/{orderId}/sync-payment")
    @Operation(summary = "Reconciliar el cobro con Mercado Pago")
    public UserPackOrderResponse syncPayment(
            @PathVariable String orderId,
            @RequestParam(value = "paymentId", required = false) String paymentId
    ) {
        return userPackService.syncMercadoPagoPayment(orderId, paymentId);
    }

    @PostMapping("/orders/{orderId}/pay")
    @Operation(summary = "Pago simulado local del paquete")
    public UserPackOrderResponse pay(
            @PathVariable String orderId,
            @Valid @RequestBody SimulatedPaymentRequest request
    ) {
        return userPackService.pay(orderId, request);
    }
}
