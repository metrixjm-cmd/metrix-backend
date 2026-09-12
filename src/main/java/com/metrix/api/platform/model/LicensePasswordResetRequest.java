package com.metrix.api.platform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Solicitud de reset de contraseña del ADMIN de una licencia.
 * Vive en la BD de plataforma para que Admin 0 vea todas las instancias.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "license_password_reset_requests")
@CompoundIndexes({
        @CompoundIndex(name = "idx_pwd_reset_lookup",
                def = "{'codigo_empresa': 1, 'numero_usuario': 1, 'status': 1, 'requested_at': -1}")
})
public class LicensePasswordResetRequest {

    @Id
    private String id;

    @Indexed
    @Field("instance_id")
    private String instanceId;

    @Field("codigo_empresa")
    private String codigoEmpresa;

    @Field("empresa_nombre")
    private String empresaNombre;

    @Field("numero_usuario")
    private String numeroUsuario;

    @Field("admin_nombre")
    private String adminNombre;

    @Field("destination_email")
    private String destinationEmail;

    @Indexed
    @Field("status")
    private LicensePasswordResetStatus status;

    @Indexed
    @Field("token_hash")
    private String tokenHash;

    @Field("expires_at")
    private Instant expiresAt;

    @Field("requested_at")
    private Instant requestedAt;

    @Field("resolved_at")
    private Instant resolvedAt;

    @Field("resolved_by")
    private String resolvedBy;

    @Field("consumed_at")
    private Instant consumedAt;

    @Field("reject_reason")
    private String rejectReason;
}
