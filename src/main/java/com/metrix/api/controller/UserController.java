package com.metrix.api.controller;

import com.metrix.api.dto.CreateUserRequest;
import com.metrix.api.dto.UpdateUserRequest;
import com.metrix.api.dto.UserResponse;
import com.metrix.api.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints del módulo de Recursos Humanos — Sprint 9.
 *
 * GET   /api/v1/users?storeId=        → lista colaboradores activos (ADMIN, GERENTE)
 * GET   /api/v1/users/{id}            → perfil individual            (ADMIN, GERENTE)
 * POST  /api/v1/users                 → crear colaborador            (ADMIN only)
 * PUT   /api/v1/users/{id}            → editar colaborador           (ADMIN, GERENTE)
 * PATCH /api/v1/users/{id}/deactivate → desactivar (soft-delete)     (ADMIN only)
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── Listar colaboradores ─────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<UserResponse>> getUsersByStore(
            @RequestParam String storeId,
            Authentication auth) {
        return ResponseEntity.ok(
                userService.getUsersByStore(storeId, auth.getName()));
    }

    // ── Perfil individual ────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // ── Crear colaborador ────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(request));
    }

    // ── Editar colaborador ───────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable String id,
            @RequestBody UpdateUserRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                userService.updateUser(id, request, auth.getName()));
    }

    // ── Desactivar colaborador (soft-delete) ─────────────────────────────

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateUser(@PathVariable String id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }
}
