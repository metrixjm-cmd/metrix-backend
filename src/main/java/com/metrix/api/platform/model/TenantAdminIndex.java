package com.metrix.api.platform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Índice global de login: resuelve {@code numeroUsuario} → BD del tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tenant_admin_index")
public class TenantAdminIndex {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("numero_usuario")
    private String numeroUsuario;

    @Field("instance_id")
    private String instanceId;

    @Field("database_name")
    private String databaseName;

    @Field("empresa_nombre")
    private String empresaNombre;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;
}
