package com.metrix.api.dto.productos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProvisionMetrixRequest {

    @NotBlank
    @Size(min = 3, max = 32)
    private String numeroUsuario;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    private String confirmPassword;

    /** Nombre del administrador del restaurante (opcional). */
    private String adminNombre;
}
