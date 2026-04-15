package com.metrix.api.dto;

import com.metrix.api.model.Role;
import com.metrix.api.validation.OlderThanYears;
import com.metrix.api.validation.RealBirthDate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

/**
 * Request para crear un nuevo colaborador desde el módulo RH.
 * Solo accesible por ADMIN.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El puesto es obligatorio")
    private String puesto;

    @NotBlank(message = "La sucursal es obligatoria")
    private String storeId;

    @NotBlank(message = "El turno es obligatorio")
    private String turno;

    /** Opcional: si se omite, se auto-genera como [PUESTO_PREFIX]+[FOLIO] (ej. CAJ001) */
    private String numeroUsuario;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @NotEmpty(message = "Debe asignar al menos un rol")
    private Set<Role> roles;

    /** Correo electrónico (opcional). */
    @Email(message = "El email no tiene un formato válido")
    private String email;

    /** Fecha de nacimiento (opcional). Formato ISO: yyyy-MM-dd */
    @RealBirthDate(message = "La fecha de nacimiento debe ser real y no puede ser futura")
    @OlderThanYears(value = 12, message = "Ingresa una fecha válida, debe tener más de 12 años cumplidos")
    private LocalDate fechaNacimiento;
}
