package com.metrix.api.controller;

import com.metrix.api.dto.CorrectionSpeedResponse;
import com.metrix.api.dto.IgeoAnalyticsResponse;
import com.metrix.api.dto.KpiSummaryResponse;
import com.metrix.api.dto.StoreRankingResponse;
import com.metrix.api.dto.UserResponsibilityResponse;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.service.KpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Controller REST de KPIs METRIX (Sprint 7).
 * <p>
 * Base path: {@code /api/v1/kpis}
 * <p>
 * Matriz de acceso:
 * <pre>
 *   GET /store/{storeId} → ADMIN, GERENTE
 *   GET /ranking         → ADMIN
 *   GET /me              → Cualquier autenticado
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/kpis")
@RequiredArgsConstructor
@Tag(name = "KPIs y Analítica", description = "Indicadores clave de rendimiento: resumen por sucursal, ranking, KPIs individuales e IGEO analítico de 4 pilares.")
@SecurityRequirement(name = "bearerAuth")
public class KpiController {

    private final KpiService     kpiService;
    private final UserRepository userRepository;

    /**
     * GET /api/v1/kpis/store/{storeId}
     * KPIs agregados de todas las tareas activas de una sucursal.
     */
    @Operation(summary = "KPIs de sucursal", description = "KPIs agregados de todas las tareas activas de una sucursal: OnTimeRate, IGEO, ReworkRate, AvgExecMin, etc.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen de KPIs de la sucursal"),
            @ApiResponse(responseCode = "403", description = "Sin permisos — requiere rol ADMIN o GERENTE")
    })
    @GetMapping("/store/{storeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<KpiSummaryResponse> getStoreSummary(
            @Parameter(description = "ID de la sucursal") @PathVariable String storeId) {
        return ResponseEntity.ok(kpiService.getStoreSummary(storeId));
    }

    /**
     * GET /api/v1/kpis/ranking
     * Ranking inter-sucursal ordenado por IGEO descendente.
     */
    @Operation(summary = "Ranking inter-sucursal", description = "Ranking de todas las sucursales ordenado por IGEO descendente. Solo accesible por ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de sucursales con su IGEO ordenado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos — requiere rol ADMIN")
    })
    @GetMapping("/ranking")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StoreRankingResponse>> getRanking() {
        return ResponseEntity.ok(kpiService.getStoreRanking());
    }

    /**
     * GET /api/v1/kpis/me
     * KPIs del usuario autenticado.
     */
    @Operation(summary = "Mis KPIs", description = "KPIs individuales del usuario autenticado: OnTimeRate, ReworkRate, AvgExecMin, IGEO personal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen de KPIs del usuario autenticado")
    })
    @GetMapping("/me")
    public ResponseEntity<KpiSummaryResponse> getMyKpis(Authentication auth) {
        return ResponseEntity.ok(kpiService.getUserSummary(resolveUserId(auth.getName())));
    }

    /**
     * GET /api/v1/kpis/store/{storeId}/users
     * KPI #7 — Ranking de colaboradores de una sucursal con sus KPIs individuales.
     */
    @Operation(summary = "KPI #7 — Ranking de colaboradores", description = "Ranking de colaboradores de una sucursal con sus KPIs individuales: IGEO, OnTimeRate, ReworkRate, tareas completadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de colaboradores con KPIs individuales"),
            @ApiResponse(responseCode = "403", description = "Sin permisos — requiere rol ADMIN o GERENTE")
    })
    @GetMapping("/store/{storeId}/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<UserResponsibilityResponse>> getUsersResponsibility(
            @Parameter(description = "ID de la sucursal") @PathVariable String storeId) {
        return ResponseEntity.ok(kpiService.getUsersResponsibility(storeId));
    }

    /**
     * GET /api/v1/kpis/store/{storeId}/correction-speed
     * KPI #9 — Velocidad de Corrección: tiempo promedio para re-ejecutar tareas fallidas.
     */
    @Operation(summary = "KPI #9 — Velocidad de corrección", description = "Tiempo promedio para re-ejecutar tareas fallidas en una sucursal. Mide ciclos FAILED → COMPLETED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos de velocidad de corrección"),
            @ApiResponse(responseCode = "403", description = "Sin permisos — requiere rol ADMIN o GERENTE")
    })
    @GetMapping("/store/{storeId}/correction-speed")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<CorrectionSpeedResponse> getCorrectionSpeed(
            @Parameter(description = "ID de la sucursal") @PathVariable String storeId) {
        return ResponseEntity.ok(kpiService.getCorrectionSpeed(storeId));
    }

    /**
     * GET /api/v1/kpis/analytics/igeo
     * <p>
     * KPI #10 — IGEO analítico calculado por el microservicio Python (Sprint 17).
     * Devuelve IGEO global + desglose por sucursal con los 4 pilares individuales:
     * Cumplimiento (40%), Tiempo (25%), Calidad (20%), Consistencia (15%).
     * <p>
     * Si el {@code analytics-service} no está disponible, responde HTTP 503
     * con un body vacío para que el frontend pueda mostrar un estado degradado.
     */
    @Operation(summary = "IGEO analítico (4 pilares)", description = "IGEO calculado por el microservicio Python: global + desglose por sucursal con Cumplimiento (40%), Tiempo (25%), Calidad (20%), Consistencia (15%). HTTP 503 si el servicio analítico no está disponible.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "IGEO global y por sucursal con desglose de pilares"),
            @ApiResponse(responseCode = "503", description = "Servicio analítico no disponible — modo degradado")
    })
    @GetMapping("/analytics/igeo")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<IgeoAnalyticsResponse> getGlobalIgeoAnalytics() {
        try {
            return ResponseEntity.ok(kpiService.getGlobalIgeoAnalytics());
        } catch (RestClientException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private String resolveUserId(String numeroUsuario) {
        return userRepository.findByNumeroUsuario(numeroUsuario)
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado en base de datos: " + numeroUsuario));
    }
}
