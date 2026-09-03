package com.metrix.api.controller;

import com.metrix.api.dto.productos.MetrixInstanceResponse;
import com.metrix.api.dto.productos.UpdateInstanceStatusRequest;
import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.service.PlatformAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Tag(name = "Plataforma", description = "Admin 0 — supervisión de instancias METRIX")
public class PlatformController {

    private final PlatformAdminService platformAdminService;

    @GetMapping("/instances")
    @Operation(summary = "Listar instancias METRIX (restaurantes clientes)")
    public ResponseEntity<List<MetrixInstanceResponse>> listInstances() {
        assertPlatformAdmin();
        return ResponseEntity.ok(platformAdminService.listInstances());
    }

    @PatchMapping("/instances/{id}/status")
    @Operation(summary = "Suspender o reactivar una instancia METRIX")
    public ResponseEntity<MetrixInstanceResponse> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateInstanceStatusRequest request) {
        assertPlatformAdmin();
        return ResponseEntity.ok(platformAdminService.updateStatus(id, request.getStatus()));
    }

    private void assertPlatformAdmin() {
        if (!TenantContext.isPlatformAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Solo Admin 0 puede administrar instancias de la plataforma.");
        }
    }
}
