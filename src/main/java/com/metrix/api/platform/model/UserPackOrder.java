package com.metrix.api.platform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cobro del único paquete adicional de usuarios de una licencia.
 * No provisiona tenant: solo extiende el cupo 30 días.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_pack_orders")
public class UserPackOrder {

    @Id
    private String id;

    @Version
    private Long version;

    @Indexed
    @Field("instance_id")
    private String instanceId;

    @Field("license_package_id")
    private String licensePackageId;

    @Field("usuarios")
    private int usuarios;

    @Field("monto")
    private BigDecimal monto;

    @Field("moneda")
    private String moneda;

    @Builder.Default
    @Field("payment_status")
    private OrderPaymentStatus paymentStatus = OrderPaymentStatus.NONE;

    @Field("payment_provider")
    private PaymentProvider paymentProvider;

    @Field("preference_id")
    private String preferenceId;

    @Indexed(sparse = true)
    @Field("mp_payment_id")
    private String mpPaymentId;

    @Field("payment_reference")
    private String paymentReference;

    @Field("paid_at")
    private Instant paidAt;

    @Field("periodo_inicio")
    private Instant periodoInicio;

    @Field("periodo_fin")
    private Instant periodoFin;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
