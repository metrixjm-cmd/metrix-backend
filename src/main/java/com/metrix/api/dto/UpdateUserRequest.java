package com.metrix.api.dto;

import com.metrix.api.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request para actualizar un colaborador existente.
 * Todos los campos son opcionales: null = no cambiar.
 * GERENTE no puede modificar el campo {@code roles}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    private String nombre;
    private String puesto;
    private String turno;

    /** Solo ADMIN puede cambiar roles. Si GERENTE envía este campo, se ignora. */
    private Set<Role> roles;
}
