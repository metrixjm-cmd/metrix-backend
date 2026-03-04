package com.metrix.api.dto;

import lombok.Data;

import java.util.List;

/**
 * Payload para editar una sucursal (PUT /api/v1/stores/{id}).
 * El campo {@code codigo} no se puede modificar una vez creado.
 * Todos los campos son opcionales — solo los no-null se aplican.
 */
@Data
public class UpdateStoreRequest {

    private String nombre;
    private String direccion;
    private String telefono;
    private List<String> turnos;
}
