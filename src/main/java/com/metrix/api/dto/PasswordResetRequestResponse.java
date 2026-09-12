package com.metrix.api.dto;

import com.metrix.api.platform.model.LicensePasswordResetStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PasswordResetRequestResponse {

    private String id;
    private String instanceId;
    private String codigoEmpresa;
    private String empresaNombre;
    private String numeroUsuario;
    private String adminNombre;
    private String destinationEmailMasked;
    private LicensePasswordResetStatus status;
    private Instant requestedAt;
    private Instant expiresAt;
    private Instant resolvedAt;
    private String resolvedBy;
    private Instant consumedAt;
    private String rejectReason;

    /** Solo en la respuesta de aprobar / disparar. Nunca en el listado. */
    private String resetUrl;
    private Boolean emailSent;
}
