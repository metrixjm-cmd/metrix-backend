package com.metrix.api.dto;

import com.metrix.api.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO de respuesta tras autenticación exitosa.
 * <p>
 * Incluye datos mínimos para que el frontend (Angular)
 * pueda hidratar el estado del usuario sin un request adicional:
 * - Token JWT para Authorization header.
 * - Roles para renderizar menús y rutas condicionales.
 * - Nombre y storeId para el header/navbar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String numeroUsuario;
    private String nombre;
    private String storeId;
    private String turno;
    private Set<Role> roles;
}
