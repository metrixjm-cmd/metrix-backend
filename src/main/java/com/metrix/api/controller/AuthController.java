package com.metrix.api.controller;

import com.metrix.api.dto.AuthRequest;
import com.metrix.api.dto.AuthResponse;
import com.metrix.api.dto.RegisterRequest;
import com.metrix.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de autenticación para METRIX.
 * <p>
 * Endpoints públicos (ver SecurityConfig):
 * - POST /api/v1/auth/register → Alta de usuarios (solo ADMIN debería invocar).
 * - POST /api/v1/auth/login    → Autenticación y obtención de JWT.
 * <p>
 * El controller es un "adaptador" delgado (Clean Architecture):
 * solo recibe, valida DTOs y delega al AuthService.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints públicos de registro e inicio de sesión. No requieren token JWT.")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Registrar usuario", description = "Crea un nuevo usuario en el sistema y devuelve un token JWT. Endpoint público, no requiere autenticación.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario registrado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de registro inválidos o usuario ya existente")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

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
