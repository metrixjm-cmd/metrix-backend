package com.metrix.api.controller;

import com.metrix.api.dto.PasswordResetRejectRequest;
import com.metrix.api.dto.PasswordResetRequestResponse;
import com.metrix.api.dto.productos.AdjustTrialRequest;
import com.metrix.api.dto.productos.MetrixInstanceResponse;
import com.metrix.api.dto.productos.UpdateInstanceStatusRequest;
import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.model.LicensePasswordResetStatus;
import com.metrix.api.platform.service.LicensePasswordResetService;
import com.metrix.api.platform.service.PlatformAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Tag(name = "Plataforma", description = "Admin 0 — supervisión de instancias METRIX")
public class PlatformController {

    private final PlatformAdminService platformAdminService;
    private final LicensePasswordResetService licensePasswordResetService;

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

    @PatchMapping("/instances/{id}/trial")
    @Operation(summary = "Sumar o restar días al periodo de prueba de una instancia")
    public ResponseEntity<MetrixInstanceResponse> adjustTrial(
            @PathVariable String id,
            @Valid @RequestBody AdjustTrialRequest request) {
        assertPlatformAdmin();
        return ResponseEntity.ok(platformAdminService.adjustTrial(id, request.getDeltaDays()));
    }

    @DeleteMapping("/instances/{id}")
    @Operation(summary = "Eliminar una instancia METRIX y su base de datos tenant")
    public ResponseEntity<Void> deleteInstance(@PathVariable String id) {
        assertPlatformAdmin();
        platformAdminService.deleteInstance(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/password-resets")
    @Operation(summary = "Listar solicitudes de reset de contraseña de licencia")
    public ResponseEntity<List<PasswordResetRequestResponse>> listPasswordResets(
            @RequestParam(required = false) LicensePasswordResetStatus status,
            @RequestParam(required = false) String instanceId) {
        assertPlatformAdmin();
        return ResponseEntity.ok(licensePasswordResetService.list(status, instanceId));
    }

    @PostMapping("/password-resets/{id}/approve")
    @Operation(summary = "Aprobar solicitud y enviar liga de reset")
    public ResponseEntity<PasswordResetRequestResponse> approvePasswordReset(
            @PathVariable String id, Authentication auth) {
        assertPlatformAdmin();
        return ResponseEntity.ok(licensePasswordResetService.approve(id, auth.getName()));
    }

    @PostMapping("/password-resets/{id}/reject")
    @Operation(summary = "Rechazar solicitud de reset")
    public ResponseEntity<Void> rejectPasswordReset(
            @PathVariable String id,
            @Valid @RequestBody(required = false) PasswordResetRejectRequest body,
            Authentication auth) {
        assertPlatformAdmin();
        String reason = body == null ? null : body.getReason();
        licensePasswordResetService.reject(id, auth.getName(), reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/instances/{id}/password-reset")
    @Operation(summary = "Admin 0 dispara reset del ADMIN de esa licencia")
    public ResponseEntity<PasswordResetRequestResponse> initiatePasswordReset(
            @PathVariable String id, Authentication auth) {
        assertPlatformAdmin();
        return ResponseEntity.ok(licensePasswordResetService.initiateForInstance(id, auth.getName()));
    }

    private void assertPlatformAdmin() {
        if (!TenantContext.isPlatformAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Solo Admin 0 puede administrar instancias de la plataforma.");
        }
    }
}
