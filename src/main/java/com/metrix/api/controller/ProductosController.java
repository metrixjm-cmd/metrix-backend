package com.metrix.api.controller;

import com.metrix.api.dto.LicensePackageResponse;
import com.metrix.api.dto.productos.*;
import com.metrix.api.platform.service.ProductCatalogService;
import com.metrix.api.platform.service.ProductOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Catálogo público y compra de planes METRIX")
public class ProductosController {

    private final ProductCatalogService catalogService;
    private final ProductOrderService orderService;

    @GetMapping("/catalog")
    @Operation(summary = "Catálogo público de planes activos")
    public ResponseEntity<List<LicensePackageResponse>> catalog() {
        return ResponseEntity.ok(catalogService.getActiveCatalog());
    }

    @GetMapping("/catalog/{packageId}")
    @Operation(summary = "Detalle público de un plan activo")
    public ResponseEntity<LicensePackageResponse> catalogPackage(@PathVariable String packageId) {
        return ResponseEntity.ok(catalogService.getActivePackage(packageId));
    }

    @PostMapping("/orders")
    @Operation(summary = "Crear orden de compra")
    public ResponseEntity<ProductOrderResponse> createOrder(
            @Valid @RequestBody CreateProductOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Consultar estado de una orden")
    public ResponseEntity<ProductOrderResponse> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @PostMapping("/orders/{orderId}/trial")
    @Operation(summary = "Iniciar periodo de prueba del plan (sin cobro)")
    public ResponseEntity<ProductOrderResponse> startTrial(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.startTrial(orderId));
    }

    @PostMapping("/orders/{orderId}/pay")
    @Operation(summary = "Simular pago de la orden")
    public ResponseEntity<ProductOrderResponse> payOrder(
            @PathVariable String orderId,
            @Valid @RequestBody SimulatedPaymentRequest request) {
        return ResponseEntity.ok(orderService.payOrder(orderId, request));
    }

    @PostMapping("/orders/{orderId}/provision")
    @Operation(summary = "Crear METRIX y administrador secundario")
    public ResponseEntity<ProvisionMetrixResponse> provisionOrder(
            @PathVariable String orderId,
            @Valid @RequestBody ProvisionMetrixRequest request) {
        return ResponseEntity.ok(orderService.provisionOrder(orderId, request));
    }
}
