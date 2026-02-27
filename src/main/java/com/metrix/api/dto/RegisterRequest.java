package com.metrix.api.dto;

import com.metrix.api.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO para registro de nuevos usuarios.
 * <p>
 * Mapea los campos de Gestión de Perfiles (Obj. #3):
 * Nombre / Puesto / Tienda / Turno / #Usuario + password y roles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El puesto es obligatorio")
    private String puesto;

    @NotBlank(message = "La tienda es obligatoria")
    private String storeId;

    @NotBlank(message = "El turno es obligatorio")
    private String turno;

    @NotBlank(message = "El número de usuario es obligatorio")
    private String numeroUsuario;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    @NotEmpty(message = "Debe asignar al menos un rol")
    private Set<Role> roles;
}
