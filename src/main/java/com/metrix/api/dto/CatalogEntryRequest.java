package com.metrix.api.dto;

import com.metrix.api.model.Role;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogEntryRequest {

    @NotBlank(message = "El valor del catálogo es obligatorio")
    private String value;

    /** Etiqueta display opcional (si no se envía, se usa value) */
    private String label;

    /** Perfil dueño del puesto (solo aplica para PUESTO). */
    private Role role;
}
