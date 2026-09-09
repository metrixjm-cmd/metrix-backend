package com.metrix.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para las solicitudes de autenticación (login).
 * <p>
 * {@code codigoEmpresa} identifica el METRIX del restaurante. Admin 0 usa
 * {@code METRIX}. Se puede omitir solo si el #Usuario sigue siendo único.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {

    @Schema(description = "Código de plataforma del restaurante. Admin 0: METRIX.",
            example = "TACOS-A3F2")
    @Size(max = 24, message = "El código de empresa no puede exceder 24 caracteres")
    @Pattern(regexp = "^$|^[A-Za-z0-9-]{3,24}$",
            message = "El código de empresa solo admite letras, números y guiones")
    private String codigoEmpresa;

    @NotBlank(message = "El número de usuario es obligatorio")
    private String numeroUsuario;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;
}
