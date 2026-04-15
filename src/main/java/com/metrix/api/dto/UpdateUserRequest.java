package com.metrix.api.dto;

import com.metrix.api.model.Role;
import com.metrix.api.validation.RealBirthDate;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    /** Solo ADMIN puede cambiar storeId (reasignar sucursal). */
    private String storeId;

    /** Solo ADMIN puede cambiar roles. Si GERENTE envía este campo, se ignora. */
    private Set<Role> roles;

    /** Correo electrónico (opcional). */
    @Email(message = "El email no tiene un formato válido")
    private String email;

    /** Fecha de nacimiento (opcional). Formato ISO: yyyy-MM-dd */
    @RealBirthDate(message = "La fecha de nacimiento debe ser real y no puede ser futura")
    private LocalDate fechaNacimiento;

}
