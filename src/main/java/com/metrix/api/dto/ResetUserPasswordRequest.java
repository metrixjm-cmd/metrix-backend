package com.metrix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request para que ADMIN regenere la contrasena de un colaborador. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetUserPasswordRequest {

    @NotBlank(message = "La contrasena del administrador es obligatoria")
    private String adminPassword;

    @NotBlank(message = "La nueva contrasena es obligatoria")
    @Size(min = 6, message = "La nueva contrasena debe tener al menos 6 caracteres")
    private String newPassword;

    @NotBlank(message = "La confirmacion de contrasena es obligatoria")
    private String confirmPassword;
}
