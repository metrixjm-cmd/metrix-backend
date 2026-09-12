package com.metrix.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PasswordResetValidateResponse {

    private boolean valid;
    private String codigoEmpresa;
    private String empresaNombre;
    private String numeroUsuario;
    private Instant expiresAt;
}
