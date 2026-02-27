package com.metrix.api.controller;

import com.metrix.api.dto.AuthRequest;
import com.metrix.api.dto.AuthResponse;
import com.metrix.api.dto.RegisterRequest;
import com.metrix.api.service.AuthService;
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
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
