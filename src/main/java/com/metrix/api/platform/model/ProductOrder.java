package com.metrix.api.platform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "product_orders")
public class ProductOrder {

    @Id
    private String id;

    @Version
    private Long version;

    @Field("status")
    private ProductOrderStatus status;

    @Field("package_snapshot")
    private ProductOrderPackageSnapshot packageSnapshot;

    @Field("empresa_nombre")
    private String empresaNombre;

    @Field("contacto_nombre")
    private String contactoNombre;

    @Field("contacto_email")
    private String contactoEmail;

    @Field("contacto_telefono")
    private String contactoTelefono;

    @Field("sucursales_contratadas")
    private int sucursalesContratadas;

    @Field("subtotal_mensual")
    private BigDecimal subtotalMensual;

    @Field("cargo_implementacion")
    private BigDecimal cargoImplementacion;

    @Field("total_cobrado")
    private BigDecimal totalCobrado;

    @Field("moneda")
    private String moneda;

    @Field("payment_reference")
    private String paymentReference;

    @Field("paid_at")
    private Instant paidAt;

    @Builder.Default
    @Field("on_trial")
    private boolean onTrial = false;

    @Field("trial_ends_at")
    private Instant trialEndsAt;

    @Field("instance_id")
    private String instanceId;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
