package com.metrix.api.controller;

import com.metrix.api.dto.CreateStoreRequest;
import com.metrix.api.dto.StoreResponse;
import com.metrix.api.dto.UpdateStoreRequest;
import com.metrix.api.service.StoreService;
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
public class StoreController {

    private final StoreService storeService;

    // ── Listar todas ─────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StoreResponse>> getAll() {
        return ResponseEntity.ok(storeService.getAll());
    }

    // ── Detalle ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<StoreResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(storeService.getById(id));
    }

    // ── Crear ─────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreResponse> create(
            @Valid @RequestBody CreateStoreRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storeService.create(request, auth.getName()));
    }

    // ── Editar ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreResponse> update(
            @PathVariable String id,
            @RequestBody UpdateStoreRequest request) {
        return ResponseEntity.ok(storeService.update(id, request));
    }

    // ── Desactivar (soft-delete) ──────────────────────────────────────────────

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        storeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
