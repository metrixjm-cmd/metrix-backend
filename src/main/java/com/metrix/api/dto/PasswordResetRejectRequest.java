package com.metrix.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRejectRequest {

    @Schema(example = "No reconocemos esta solicitud.")
    @Size(max = 280, message = "El motivo no puede exceder 280 caracteres")
    private String reason;
}
