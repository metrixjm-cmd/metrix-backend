package com.metrix.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Payload para crear una sucursal (POST /api/v1/stores).
 */
@Data
public class CreateStoreRequest {

    @NotBlank(message = "El nombre de la sucursal es obligatorio")
    private String nombre;

    @NotBlank(message = "El código de la sucursal es obligatorio")
    private String codigo;

    private String direccion;
    private String telefono;

    /** Si se omite, se usarán los 3 turnos estándar. */
    private List<String> turnos;
}
