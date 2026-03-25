package com.metrix.api.dto;

import com.metrix.api.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * DTO de respuesta para el perfil de un colaborador.
 * Omite el campo {@code password} por seguridad.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String id;
    private String nombre;
    private String puesto;
    private String storeId;
    private String turno;
    private String numeroUsuario;
    private Set<Role> roles;
    private boolean activo;
    private String email;
    private LocalDate fechaNacimiento;
    private Instant createdAt;
    private Instant updatedAt;
}
