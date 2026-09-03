package com.metrix.api.controller;

import com.metrix.api.dto.LicensePackageResponse;
import com.metrix.api.dto.UpdateLicensePackageRequest;
import com.metrix.api.service.LicensePackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/license-packages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Tag(name = "Licencias", description = "Catálogo comercial de paquetes METRIX — solo Admin 0")
public class LicensePackageController {

    private final LicensePackageService licensePackageService;

    @GetMapping
    @Operation(summary = "Listar paquetes de licencia")
    public ResponseEntity<List<LicensePackageResponse>> getAll() {
        return ResponseEntity.ok(licensePackageService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un paquete")
    public ResponseEntity<LicensePackageResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(licensePackageService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar paquete")
    public ResponseEntity<LicensePackageResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateLicensePackageRequest request) {
        return ResponseEntity.ok(licensePackageService.update(id, request));
    }

    @PatchMapping("/{id}/activo")
    @Operation(summary = "Activar o desactivar paquete")
    public ResponseEntity<LicensePackageResponse> toggleActivo(@PathVariable String id) {
        return ResponseEntity.ok(licensePackageService.toggleActivo(id));
    }

    @PatchMapping("/{id}/destacado")
    @Operation(summary = "Marcar o desmarcar paquete recomendado")
    public ResponseEntity<LicensePackageResponse> toggleDestacado(@PathVariable String id) {
        return ResponseEntity.ok(licensePackageService.toggleDestacado(id));
    }

    @PostMapping("/reset-defaults")
    @Operation(summary = "Restablecer los 4 paquetes a los valores de plantilla")
    public ResponseEntity<List<LicensePackageResponse>> resetDefaults() {
        return ResponseEntity.ok(licensePackageService.resetDefaults());
    }
}
