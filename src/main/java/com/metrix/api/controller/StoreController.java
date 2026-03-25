package com.metrix.api.controller;

import com.metrix.api.dto.CreateStoreRequest;
import com.metrix.api.dto.StoreResponse;
import com.metrix.api.dto.UpdateStoreRequest;
import com.metrix.api.service.SequenceService;
import com.metrix.api.service.StoreService;
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
 * Endpoints del módulo de Configuración / Sucursales — Sprint 11.
 *
 * GET    /api/v1/stores          → lista todas las sucursales activas    (ADMIN)
 * GET    /api/v1/stores/{id}     → detalle + stats de una sucursal       (ADMIN, GERENTE)
 * POST   /api/v1/stores          → crear sucursal                        (ADMIN)
 * PUT    /api/v1/stores/{id}     → editar sucursal                       (ADMIN)
 * PATCH  /api/v1/stores/{id}/deactivate → soft-delete                    (ADMIN)
 */
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
@Tag(name = "Sucursales", description = "Configuración de sucursales (Sprint 11)")
public class StoreController {

    private final StoreService    storeService;
    private final SequenceService sequenceService;

    // ── Preview del próximo código ────────────────────────────────────────────

    @Operation(summary = "Preview del próximo código de sucursal",
               description = "Devuelve el código que se asignará a la siguiente sucursal (sin consumirlo). Útil para mostrarlo en el formulario.")
    @ApiResponse(responseCode = "200", description = "Próximo código disponible")
    @GetMapping("/next-code")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.Map<String, String>> getNextCode() {
        return ResponseEntity.ok(java.util.Map.of("codigo", sequenceService.peekNextStoreCode()));
    }

    // ── Listar todas ─────────────────────────────────────────────────────────

    @Operation(summary = "Listar sucursales activas",
               description = "Devuelve todas las sucursales activas del sistema. Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de sucursales obtenida exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<StoreResponse>> getAll() {
        return ResponseEntity.ok(storeService.getAll());
    }

    // ── Detalle ───────────────────────────────────────────────────────────────

    @Operation(summary = "Detalle de sucursal con estadísticas",
               description = "Devuelve la información de una sucursal incluyendo conteos de usuarios, tareas y capacitaciones.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de sucursal obtenido exitosamente"),
            @ApiResponse(responseCode = "404", description = "Sucursal no encontrada")
    })
    @GetMapping("/{id:[a-f0-9]{24}}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<StoreResponse> getById(@Parameter(description = "ID de la sucursal") @PathVariable String id) {
        return ResponseEntity.ok(storeService.getById(id));
    }

    // ── Crear ─────────────────────────────────────────────────────────────────

    @Operation(summary = "Crear sucursal",
               description = "Registra una nueva sucursal en el sistema. Valida código único. Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sucursal creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos o código duplicado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreResponse> create(
            @Valid @RequestBody CreateStoreRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storeService.create(request, auth.getName()));
    }

    // ── Editar ────────────────────────────────────────────────────────────────

    @Operation(summary = "Editar sucursal",
               description = "Actualiza los datos de una sucursal existente. Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sucursal actualizada exitosamente"),
            @ApiResponse(responseCode = "404", description = "Sucursal no encontrada"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos")
    })
    @PutMapping("/{id:[a-f0-9]{24}}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreResponse> update(
            @Parameter(description = "ID de la sucursal") @PathVariable String id,
            @RequestBody UpdateStoreRequest request) {
        return ResponseEntity.ok(storeService.update(id, request));
    }

    // ── Desactivar (soft-delete) ──────────────────────────────────────────────

    @Operation(summary = "Desactivar sucursal",
               description = "Desactiva una sucursal sin eliminarla de la base de datos (soft-delete). Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sucursal desactivada exitosamente"),
            @ApiResponse(responseCode = "404", description = "Sucursal no encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes")
    })
    @PatchMapping("/{id:[a-f0-9]{24}}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@Parameter(description = "ID de la sucursal") @PathVariable String id) {
        storeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
