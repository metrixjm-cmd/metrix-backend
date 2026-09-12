package com.metrix.api.controller;

import com.metrix.api.dto.PasswordResetConfirmRequest;
import com.metrix.api.dto.PasswordResetRequestBody;
import com.metrix.api.dto.PasswordResetValidateResponse;
import com.metrix.api.platform.service.LicensePasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reset público de contraseña del ADMIN de una licencia.
 * Admin 0 aprueba; la liga es de un solo uso.
 */
@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Inicio de sesión y reset de contraseña de licencia.")
public class AuthPasswordResetController {

    private final LicensePasswordResetService licensePasswordResetService;

    @Operation(
            summary = "Solicitar reset (olvidé mi contraseña)",
            description = "Público. Siempre 204 si el body es válido, exista o no el ADMIN. "
                    + "No aplica a codigoEmpresa=METRIX.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Aceptado. Mensaje genérico en UI."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes para esa cuenta")
    })
    @PostMapping("/request")
    public ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequestBody body) {
        licensePasswordResetService.requestReset(body);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Validar token de la liga")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token vigente"),
            @ApiResponse(responseCode = "422", description = "Token inválido, vencido o ya usado")
    })
    @GetMapping("/validate")
    public ResponseEntity<PasswordResetValidateResponse> validate(@RequestParam("token") String token) {
        return ResponseEntity.ok(licensePasswordResetService.validateToken(token));
    }

    @Operation(summary = "Consumir la liga y guardar la nueva contraseña")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contraseña actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "422", description = "Token inválido/vencido/usado")
    })
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest body) {
        licensePasswordResetService.confirm(body);
        return ResponseEntity.noContent().build();
    }
}
