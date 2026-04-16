package com.metrix.api.controller;

import com.metrix.api.dto.CatalogEntryRequest;
import com.metrix.api.dto.CatalogEntryResponse;
import com.metrix.api.model.Catalog;
import com.metrix.api.model.Role;
import com.metrix.api.repository.CatalogRepository;
import com.metrix.api.service.PuestoCatalogPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * API de Catálogos Dinámicos — alimenta todos los dropdowns del frontend.
 * <p>
 * Tipos soportados: PUESTO, TURNO, CATEGORIA_TAREA, CATEGORIA_INCIDENCIA
 * <p>
 * GET  /api/v1/catalogs/{type}     → lista valores activos de un tipo (cualquier auth)
 * POST /api/v1/catalogs/{type}     → agregar nuevo valor (ADMIN/GERENTE)
 */
@RestController
@RequestMapping("/api/v1/catalogs")
@RequiredArgsConstructor
@Tag(name = "Catálogos", description = "Catálogos dinámicos que alimentan dropdowns del sistema")
public class CatalogController {

    private final CatalogRepository catalogRepository;

    @GetMapping("/{type}")
    @Operation(summary = "Listar valores de un catálogo",
            description = "Devuelve todos los valores activos del tipo especificado (PUESTO, TURNO, etc.)")
    @ApiResponse(responseCode = "200", description = "Lista de valores del catálogo")
    public ResponseEntity<List<CatalogEntryResponse>> getByType(
            @Parameter(description = "Tipo de catálogo: PUESTO, TURNO, CATEGORIA_TAREA, CATEGORIA_INCIDENCIA")
            @PathVariable String type,
            @Parameter(description = "Perfil para filtrar puestos: ADMIN, GERENTE, EJECUTADOR")
            @RequestParam(required = false) Role role) {

        String normalizedType = type.toUpperCase();
        if ("PUESTO".equals(normalizedType)) {
            return ResponseEntity.ok(getPuestos(role));
        }

        List<CatalogEntryResponse> entries = catalogRepository
                .findByTypeAndActivoTrue(normalizedType)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(entries);
    }

    @PostMapping("/{type}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    @Operation(summary = "Agregar valor a un catálogo",
            description = "Crea una nueva entrada en el catálogo. Útil para agregar puestos, turnos, etc. sin perder progreso en formularios.")
    @ApiResponse(responseCode = "201", description = "Entrada creada exitosamente")
    @ApiResponse(responseCode = "409", description = "El valor ya existe en este catálogo")
    public ResponseEntity<CatalogEntryResponse> addEntry(
            @PathVariable String type,
            @Valid @RequestBody CatalogEntryRequest request) {

        String normalizedType = type.toUpperCase();
        String normalizedValue = request.getValue().trim();
        Role catalogRole = resolveCatalogRole(normalizedType, request);

        if (catalogRepository.existsByTypeAndValue(normalizedType, normalizedValue)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Catalog catalog = Catalog.builder()
                .type(normalizedType)
                .value(normalizedValue)
                .label(request.getLabel() != null ? request.getLabel().trim() : normalizedValue)
                .role(catalogRole)
                .build();

        Catalog saved = catalogRepository.save(catalog);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PutMapping("/{type}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    @Operation(summary = "Editar entrada de catálogo")
    @ApiResponse(responseCode = "200", description = "Entrada actualizada")
    @ApiResponse(responseCode = "404", description = "Entrada no encontrada")
    public ResponseEntity<CatalogEntryResponse> updateEntry(
            @PathVariable String type,
            @PathVariable String id,
            @Valid @RequestBody CatalogEntryRequest request) {

        return catalogRepository.findById(id)
                .map(catalog -> {
                    String normalizedType = type.toUpperCase();
                    String normalizedValue = request.getValue().trim();
                    Role catalogRole = resolveCatalogRole(normalizedType, request);
                    catalog.setValue(normalizedValue);
                    catalog.setLabel(request.getLabel() != null
                            ? request.getLabel().trim()
                            : normalizedValue);
                    if ("PUESTO".equals(normalizedType)) {
                        catalog.setRole(catalogRole);
                    }
                    return ResponseEntity.ok(toResponse(catalogRepository.save(catalog)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{type}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    @Operation(summary = "Desactivar entrada de catálogo (soft-delete)")
    @ApiResponse(responseCode = "204", description = "Entrada desactivada")
    public ResponseEntity<Void> deleteEntry(
            @PathVariable String type,
            @PathVariable String id) {

        var opt = catalogRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        var catalog = opt.get();
        catalog.setActivo(false);
        catalogRepository.save(catalog);
        return ResponseEntity.noContent().build();
    }

    private List<CatalogEntryResponse> getPuestos(Role role) {
        List<CatalogEntryResponse> entries = new ArrayList<>();
        if (role == null || role == Role.ADMIN) {
            entries.add(CatalogEntryResponse.builder()
                    .id("admin-fixed")
                    .type("PUESTO")
                    .value(PuestoCatalogPolicy.ADMIN_PUESTO)
                    .label(PuestoCatalogPolicy.ADMIN_PUESTO)
                    .role(Role.ADMIN)
                    .build());
        }

        catalogRepository.findByTypeAndActivoTrue("PUESTO").stream()
                .filter(catalog -> PuestoCatalogPolicy.resolveRole(catalog) != Role.ADMIN)
                .filter(catalog -> role == null || PuestoCatalogPolicy.resolveRole(catalog) == role)
                .map(this::toResponse)
                .forEach(entries::add);

        return entries;
    }

    private Role resolveCatalogRole(String normalizedType, CatalogEntryRequest request) {
        if (!"PUESTO".equals(normalizedType)) {
            return null;
        }
        Role requestedRole = request.getRole() != null
                ? request.getRole()
                : PuestoCatalogPolicy.inferRole(request.getValue());
        if (requestedRole == Role.ADMIN) {
            throw new IllegalArgumentException("El puesto de administrador es fijo y no se puede editar desde catalogos.");
        }
        return requestedRole;
    }

    private CatalogEntryResponse toResponse(Catalog c) {
        return CatalogEntryResponse.builder()
                .id(c.getId())
                .type(c.getType())
                .value(c.getValue())
                .label(c.getLabel() != null ? c.getLabel() : c.getValue())
                .role("PUESTO".equals(c.getType()) ? PuestoCatalogPolicy.resolveRole(c) : c.getRole())
                .build();
    }
}
