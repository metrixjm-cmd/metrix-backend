package com.metrix.api.controller;

import com.metrix.api.dto.CreateUserRequest;
import com.metrix.api.dto.UpdateUserRequest;
import com.metrix.api.dto.UserResponse;
import com.metrix.api.service.SequenceService;
import com.metrix.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Recursos Humanos", description = "Gestión de colaboradores (Sprint 9)")
public class UserController {

    private final UserService     userService;
    private final SequenceService sequenceService;

    // ── Todos los colaboradores (ADMIN) ─────────────────────────────────

    @Operation(summary = "Todos los colaboradores activos", description = "Solo ADMIN. Devuelve todos los usuarios activos del sistema.")
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ── Preview del próximo folio ────────────────────────────────────────

    @Operation(summary = "Preview del próximo número de usuario",
               description = "Devuelve el folio que se asignará al siguiente colaborador del puesto indicado (sin consumirlo).")
    @GetMapping("/next-folio")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<java.util.Map<String, String>> getNextFolio(
            @Parameter(description = "Rol principal: ADMIN, GERENTE, EJECUTADOR")
            @RequestParam(required = false) String rol,
            @Parameter(description = "Nombre del puesto, ej. Cajero")
            @RequestParam(defaultValue = "COLABORADOR") String puesto) {
        return ResponseEntity.ok(
                java.util.Map.of("numeroUsuario", sequenceService.peekNextUserFolio(rol, puesto)));
    }

    // ── Listar colaboradores ─────────────────────────────────────────────

    @Operation(summary = "Listar colaboradores por sucursal",
               description = "Devuelve todos los colaboradores activos de una sucursal. Requiere rol ADMIN o GERENTE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de colaboradores obtenida exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<UserResponse>> getUsersByStore(
            @Parameter(description = "ID de la sucursal") @RequestParam String storeId,
            Authentication auth) {
        return ResponseEntity.ok(
                userService.getUsersByStore(storeId, auth.getName()));
    }

    // ── Perfil individual ────────────────────────────────────────────────

    @Operation(summary = "Perfil de colaborador",
               description = "Devuelve el perfil completo de un colaborador por su ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil obtenido exitosamente"),
            @ApiResponse(responseCode = "404", description = "Colaborador no encontrado")
    })
    @GetMapping("/{id:[a-f0-9]{24}}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<UserResponse> getUserById(@Parameter(description = "ID del colaborador") @PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // ── Crear colaborador ────────────────────────────────────────────────

    @Operation(summary = "Crear colaborador",
               description = "Registra un nuevo colaborador en el sistema. Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Colaborador creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(request));
    }

    // ── Editar colaborador ───────────────────────────────────────────────

    @Operation(summary = "Editar colaborador",
               description = "Actualiza los datos de un colaborador existente. ADMIN o GERENTE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Colaborador actualizado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Colaborador no encontrado"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos")
    })
    @PutMapping("/{id:[a-f0-9]{24}}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "ID del colaborador") @PathVariable String id,
            @RequestBody UpdateUserRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                userService.updateUser(id, request, auth.getName()));
    }

    // ── Desactivar colaborador (soft-delete) ─────────────────────────────

    @Operation(summary = "Desactivar colaborador (soft-delete)",
               description = "Desactiva un colaborador sin eliminarlo de la base de datos. Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Colaborador desactivado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Colaborador no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    @PatchMapping("/{id:[a-f0-9]{24}}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateUser(@Parameter(description = "ID del colaborador") @PathVariable String id) {
        userService.deactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    // ── Eliminar colaborador (hard-delete) ───────────────────────────────

    @Operation(summary = "Eliminar colaborador permanentemente",
               description = "Elimina el registro del colaborador de la base de datos. Solo ADMIN. Irreversible.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Colaborador eliminado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Colaborador no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    @DeleteMapping("/{id:[a-f0-9]{24}}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@Parameter(description = "ID del colaborador") @PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
