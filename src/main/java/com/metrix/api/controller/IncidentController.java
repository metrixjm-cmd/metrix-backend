package com.metrix.api.controller;

import com.metrix.api.dto.CreateIncidentRequest;
import com.metrix.api.dto.IncidentResponse;
import com.metrix.api.dto.UpdateIncidentStatusRequest;
import com.metrix.api.model.IncidentStatus;
import com.metrix.api.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
@Tag(name = "Incidencias", description = "Módulo de contingencias (Sprint 15)")
public class IncidentController {

    private final IncidentService incidentService;

    /** Crear incidencia — cualquier usuario autenticado. */
    @Operation(summary = "Reportar incidencia", description = "Crea una nueva incidencia/contingencia. Cualquier usuario autenticado puede reportar.")
    @ApiResponse(responseCode = "201", description = "Incidencia creada exitosamente")
    @PostMapping
    public ResponseEntity<IncidentResponse> create(
            @Valid @RequestBody CreateIncidentRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(incidentService.create(request, auth.getName()));
    }

    /** Incidencias visibles por jerarquía — cualquier usuario autenticado. */
    @Operation(summary = "Incidencias visibles", description = "Retorna las incidencias según la jerarquía: "
            + "ADMIN ve gerentes + propias, GERENTE ve ejecutadores de su sucursal + propias, EJECUTADOR ve solo propias.")
    @ApiResponse(responseCode = "200", description = "Lista de incidencias según jerarquía")
    @GetMapping("/visible")
    public List<IncidentResponse> getVisibleIncidents(Authentication auth) {
        return incidentService.getVisibleForUser(auth.getName());
    }

    /** Mis incidencias — cualquier usuario autenticado. */
    @Operation(summary = "Mis incidencias", description = "Lista las incidencias reportadas por el usuario autenticado.")
    @ApiResponse(responseCode = "200", description = "Lista de incidencias del usuario")
    @GetMapping("/my")
    public List<IncidentResponse> getMyIncidents(Authentication auth) {
        return incidentService.getMyIncidents(auth.getName());
    }

    /** Incidencias por sucursal — solo ADMIN/GERENTE. Filtro opcional ?status= */
    @Operation(summary = "Incidencias por sucursal", description = "Lista las incidencias de una sucursal. Filtro opcional por estado. Solo ADMIN/GERENTE.")
    @ApiResponse(responseCode = "200", description = "Lista de incidencias de la sucursal")
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
    @Operation(summary = "Detalle de incidencia", description = "Obtiene el detalle completo de una incidencia por su ID.")
    @ApiResponse(responseCode = "200", description = "Detalle de la incidencia")
    @GetMapping("/{id}")
    public IncidentResponse getById(@PathVariable String id) {
        return incidentService.getById(id);
    }

    /** Cambiar estado del ciclo de vida — solo ADMIN/GERENTE. */
    @Operation(summary = "Cambiar estado de incidencia", description = "Actualiza el estado en el ciclo de vida de la incidencia (ABIERTA → EN_RESOLUCION → CERRADA). Solo ADMIN/GERENTE.")
    @ApiResponse(responseCode = "200", description = "Incidencia actualizada exitosamente")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public IncidentResponse updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateIncidentStatusRequest request,
            Authentication auth) {
        return incidentService.updateStatus(id, request, auth.getName());
    }

    /** Subir evidencia multimedia — cualquier usuario autenticado. */
    @Operation(summary = "Subir evidencia a incidencia", description = "Adjunta un archivo multimedia como evidencia a una incidencia existente.")
    @ApiResponse(responseCode = "200", description = "Evidencia subida y asociada exitosamente")
    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IncidentResponse uploadEvidence(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        return incidentService.uploadEvidence(id, file, auth.getName());
    }

    /** Purge — elimina TODAS las incidencias (solo ADMIN, uso en pruebas). */
    @Operation(summary = "Purge all incidents", description = "Elimina todas las incidencias. Solo ADMIN.")
    @ApiResponse(responseCode = "204", description = "Todas las incidencias eliminadas")
    @DeleteMapping("/admin/purge")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purgeAll() {
        incidentService.deleteAll();
    }
}
