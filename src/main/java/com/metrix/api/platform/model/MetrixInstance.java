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

import java.time.Instant;

/**
 * Registro de una instancia METRIX (restaurante cliente) con BD dedicada.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "metrix_instances")
public class MetrixInstance {

    @Id
    private String id;

    @Version
    private Long version;

    @Indexed(unique = true)
    @Field("database_name")
    private String databaseName;

    @Field("empresa_nombre")
    private String empresaNombre;

    @Field("license_package_id")
    private String licensePackageId;

    @Field("license_package_nombre")
    private String licensePackageNombre;

    @Field("order_id")
    private String orderId;

    @Field("admin_numero_usuario")
    private String adminNumeroUsuario;

    @Field("admin_nombre")
    private String adminNombre;

    @Field("contacto_email")
    private String contactoEmail;

    @Builder.Default
    @Field("status")
    private MetrixInstanceStatus status = MetrixInstanceStatus.ACTIVE;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;
}
