package com.metrix.api.controller;

import com.metrix.api.dto.AuthRequest;
import com.metrix.api.dto.AuthResponse;
import com.metrix.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de autenticación para METRIX.
 * <p>
 * Único endpoint público (ver SecurityConfig): {@code POST /api/v1/auth/login}.
 * <p>
 * El alta de usuarios vive en {@code POST /api/v1/users}, que aplica la política de
 * roles (un GERENTE sólo crea EJECUTADOR de su sucursal). Aquí existía un
 * {@code /register} público que aceptaba el rol desde el cuerpo de la petición y
 * devolvía el JWT: bastaba un POST sin token para obtener una cuenta ADMIN. Se
 * eliminó en vez de protegerlo porque nadie lo consumía.
 * <p>
 * El controller es un "adaptador" delgado (Clean Architecture):
 * solo recibe, valida DTOs y delega al AuthService.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Inicio de sesión. No requiere token JWT.")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Iniciar sesión", description = "Autentica al usuario con número de usuario y contraseña, devuelve un token JWT válido.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa, token JWT devuelto"),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
