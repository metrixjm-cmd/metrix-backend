package com.metrix.api.controller;

import com.metrix.api.dto.CreateIncidentRequest;
import com.metrix.api.dto.IncidentResponse;
import com.metrix.api.dto.UpdateIncidentStatusRequest;
import com.metrix.api.model.IncidentStatus;
import com.metrix.api.service.IncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST del módulo de Contingencias / Incidencias (Sprint 15).
 * <p>
 * Base: /api/v1/incidents
 * <p>
 * Roles:
 * <ul>
 *   <li>EJECUTADOR → POST / y GET /my y GET /{id}</li>
 *   <li>GERENTE/ADMIN → todo lo anterior + GET /store/{storeId} + PATCH /{id}/status</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    /** Crear incidencia — cualquier usuario autenticado. */
    @PostMapping
    public ResponseEntity<IncidentResponse> create(
            @Valid @RequestBody CreateIncidentRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(incidentService.create(request, auth.getName()));
    }

    /** Mis incidencias — cualquier usuario autenticado. */
    @GetMapping("/my")
    public List<IncidentResponse> getMyIncidents(Authentication auth) {
        return incidentService.getMyIncidents(auth.getName());
    }

    /** Incidencias por sucursal — solo ADMIN/GERENTE. Filtro opcional ?status= */
    @GetMapping("/store/{storeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<IncidentResponse> getByStore(
            @PathVariable String storeId,
            @RequestParam(required = false) IncidentStatus status) {
        if (status != null) {
            return incidentService.getByStoreAndStatus(storeId, status);
        }
        return incidentService.getByStore(storeId);
    }

    /** Detalle de una incidencia — cualquier usuario autenticado. */
    @GetMapping("/{id}")
    public IncidentResponse getById(@PathVariable String id) {
        return incidentService.getById(id);
    }

    /** Cambiar estado del ciclo de vida — solo ADMIN/GERENTE. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public IncidentResponse updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateIncidentStatusRequest request,
            Authentication auth) {
        return incidentService.updateStatus(id, request, auth.getName());
    }
}
