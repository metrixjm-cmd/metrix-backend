package com.metrix.api.dto;

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
}
