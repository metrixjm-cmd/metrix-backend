package com.metrix.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud pública de reset de contraseña del ADMIN de una licencia")
public class PasswordResetRequestBody {

    @Schema(description = "Código de plataforma del restaurante. No aplica a METRIX (Admin 0).",
            example = "TACOS-A3F2")
    @NotBlank(message = "El código de empresa es obligatorio")
    @Size(max = 24, message = "El código de empresa no puede exceder 24 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9-]{3,24}$",
            message = "El código de empresa solo admite letras, números y guiones")
    private String codigoEmpresa;

    @Schema(example = "ADMIN001")
    @NotBlank(message = "El número de usuario es obligatorio")
    private String numeroUsuario;
}
