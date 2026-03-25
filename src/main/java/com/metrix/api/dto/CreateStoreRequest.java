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

    /** Opcional: si se omite, se auto-genera como SUC001, SUC002, etc. */
    private String codigo;

    private String direccion;
    private String telefono;

    /** Si se omite, se usarán los 3 turnos estándar. */
    private List<String> turnos;
}
