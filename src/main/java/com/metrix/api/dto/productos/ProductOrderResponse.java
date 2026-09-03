package com.metrix.api.dto.productos;

import com.metrix.api.dto.LicensePackageResponse;
import com.metrix.api.platform.model.ProductOrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ProductOrderResponse {

    private String id;
    private ProductOrderStatus status;
    private ProductOrderPackageSnapshotResponse packageSnapshot;
    private String empresaNombre;
    private String contactoNombre;
    private String contactoEmail;
    private String contactoTelefono;
    private int sucursalesContratadas;
    private BigDecimal subtotalMensual;
    private BigDecimal cargoImplementacion;
    private BigDecimal totalCobrado;
    private String moneda;
    private String paymentReference;
    private Instant paidAt;
    private boolean onTrial;
    private Instant trialEndsAt;
    private String instanceId;
    private Instant createdAt;
}
