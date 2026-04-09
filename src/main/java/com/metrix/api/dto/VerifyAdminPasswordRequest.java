package com.metrix.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request para confirmar la contrasena actual del administrador. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyAdminPasswordRequest {

    @NotBlank(message = "La contrasena del administrador es obligatoria")
    private String adminPassword;
}
