package com.metrix.api.platform.service;

import com.metrix.api.dto.productos.*;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.LicensePackage;
import com.metrix.api.platform.model.*;
import com.metrix.api.platform.repository.LicensePackageRepository;
import com.metrix.api.platform.repository.ProductOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductOrderService {

    private final ProductOrderRepository orderRepository;
    private final LicensePackageRepository licensePackageRepository;
    private final ProductPricingCalculator pricingCalculator;
    private final PaymentGateway paymentGateway;
    private final MetrixProvisioningService provisioningService;
    private final TenantUserIndexService tenantUserIndexService;

    public ProductOrderResponse createOrder(CreateProductOrderRequest request) {
        LicensePackage pkg = licensePackageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Paquete no encontrado"));

        if (!pkg.isActivo()) {
            throw new IllegalStateException("El paquete no está disponible para compra.");
        }

        int sucursales = request.getSucursalesContratadas() != null
                ? request.getSucursalesContratadas() : 1;
        ProductPricingCalculator.PricingBreakdown pricing =
                pricingCalculator.calculate(request.getPackageId(), sucursales);

        ProductOrder order = ProductOrder.builder()
                .status(ProductOrderStatus.DRAFT)
                .packageSnapshot(toSnapshot(pkg))
                .empresaNombre(request.getEmpresaNombre().trim())
                .contactoNombre(request.getContactoNombre().trim())
                .contactoEmail(request.getContactoEmail().trim().toLowerCase(Locale.ROOT))
                .contactoTelefono(trimOrNull(request.getContactoTelefono()))
                .sucursalesContratadas(sucursales)
                .subtotalMensual(pricing.subtotalMensual())
                .cargoImplementacion(pricing.cargoImplementacion())
                .totalCobrado(pricing.totalCobrado())
                .moneda(pricing.moneda())
                .build();

        order.setStatus(ProductOrderStatus.PENDING_PAYMENT);
        return toResponse(orderRepository.save(order));
    }

    public ProductOrderResponse getOrder(String orderId) {
        return toResponse(findOrder(orderId));
    }

    public ProductOrderResponse payOrder(String orderId, SimulatedPaymentRequest paymentRequest) {
        ProductOrder order = findOrder(orderId);
        if (order.getStatus() == ProductOrderStatus.PROVISIONED) {
            throw new IllegalStateException("La orden ya fue provisionada.");
        }
        if (order.getStatus() == ProductOrderStatus.CANCELLED) {
            throw new IllegalStateException("La orden está cancelada.");
        }

        PaymentGateway.PaymentResult result = paymentGateway.charge(
                order.getTotalCobrado(), order.getMoneda(), paymentRequest);

        if (!result.success()) {
            throw new IllegalStateException(result.message());
        }

        order.setStatus(ProductOrderStatus.PAID);
        order.setPaymentReference(result.reference());
        order.setPaidAt(Instant.now());
        return toResponse(orderRepository.save(order));
    }

    public ProvisionMetrixResponse provisionOrder(String orderId, ProvisionMetrixRequest request) {
        ProductOrder order = findOrder(orderId);
        if (order.getStatus() != ProductOrderStatus.PAID) {
            throw new IllegalStateException("La orden debe estar pagada antes de crear el administrador.");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Las contraseñas no coinciden.");
        }

        String numeroUsuario = request.getNumeroUsuario().trim().toUpperCase(Locale.ROOT);
        validateUsernameAvailable(numeroUsuario);

        MetrixInstance instance = provisioningService.provision(
                order,
                numeroUsuario,
                request.getPassword(),
                trimOrNull(request.getAdminNombre())
        );

        order.setStatus(ProductOrderStatus.PROVISIONED);
        order.setInstanceId(instance.getId());
        orderRepository.save(order);

        return ProvisionMetrixResponse.builder()
                .instanceId(instance.getId())
                .databaseName(instance.getDatabaseName())
                .adminNumeroUsuario(instance.getAdminNumeroUsuario())
                .loginUrl("/auth/login")
                .message("METRIX creado correctamente. Inicia sesión con tus credenciales.")
                .build();
    }

    private void validateUsernameAvailable(String numeroUsuario) {
        tenantUserIndexService.assertNumeroUsuarioAvailable(numeroUsuario);
    }

    private ProductOrder findOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + orderId));
    }

    private ProductOrderPackageSnapshot toSnapshot(LicensePackage pkg) {
        return ProductOrderPackageSnapshot.builder()
                .packageId(pkg.getId())
                .nombre(pkg.getNombre())
                .etiqueta(pkg.getEtiqueta())
                .pricingModel(pkg.getPricingModel())
                .precioMensual(pkg.getPrecioMensual())
                .precioImplementacion(pkg.getPrecioImplementacion())
                .moneda(pkg.getMoneda())
                .maxUsuarios(pkg.getMaxUsuarios())
                .maxSucursales(pkg.getMaxSucursales())
                .accent(pkg.getAccent())
                .build();
    }

    private ProductOrderResponse toResponse(ProductOrder order) {
        ProductOrderPackageSnapshot snap = order.getPackageSnapshot();
        return ProductOrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .packageSnapshot(snap == null ? null : ProductOrderPackageSnapshotResponse.builder()
                        .packageId(snap.getPackageId())
                        .nombre(snap.getNombre())
                        .etiqueta(snap.getEtiqueta())
                        .pricingModel(snap.getPricingModel())
                        .precioMensual(snap.getPrecioMensual())
                        .precioImplementacion(snap.getPrecioImplementacion())
                        .moneda(snap.getMoneda())
                        .maxUsuarios(snap.getMaxUsuarios())
                        .maxSucursales(snap.getMaxSucursales())
                        .accent(snap.getAccent())
                        .build())
                .empresaNombre(order.getEmpresaNombre())
                .contactoNombre(order.getContactoNombre())
                .contactoEmail(order.getContactoEmail())
                .contactoTelefono(order.getContactoTelefono())
                .sucursalesContratadas(order.getSucursalesContratadas())
                .subtotalMensual(order.getSubtotalMensual())
                .cargoImplementacion(order.getCargoImplementacion())
                .totalCobrado(order.getTotalCobrado())
                .moneda(order.getMoneda())
                .paymentReference(order.getPaymentReference())
                .paidAt(order.getPaidAt())
                .instanceId(order.getInstanceId())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
