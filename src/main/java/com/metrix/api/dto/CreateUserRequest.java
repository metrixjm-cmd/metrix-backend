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
 * Request para crear un nuevo colaborador desde el modulo RH.
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

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
    private String password;

    @NotEmpty(message = "Debe asignar al menos un rol")
    private Set<Role> roles;

    /** Correo electronico (opcional). */
    @Email(message = "El email no tiene un formato valido")
    private String email;

    /** Fecha de nacimiento (opcional). Formato ISO: yyyy-MM-dd */
    @RealBirthDate(message = "La fecha de nacimiento debe ser real y no puede ser futura")
    @OlderThanYears(value = 17, message = "Ingresa una fecha valida, debe tener mas de 17 años cumplidos")
    private LocalDate fechaNacimiento;
}
