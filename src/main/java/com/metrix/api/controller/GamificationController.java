package com.metrix.api.controller;

import com.metrix.api.dto.ExamLeaderboardResponse;
import com.metrix.api.dto.GamificationSummaryDTO;
import com.metrix.api.dto.LeaderboardEntryDTO;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.User;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.service.GamificationService;
import com.metrix.api.service.RolePolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST del módulo de Gamificación — Sprint 12.
 * <p>
 * Base path: {@code /api/v1/gamification}
 * <p>
 * Matriz de acceso:
 * <pre>
 *   GET /store/{storeId}/leaderboard   → ADMIN, GERENTE  (ranking de la sucursal)
 *   GET /me                            → cualquier autenticado (resumen personal)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/gamification")
@RequiredArgsConstructor
@Tag(name = "Gamificación", description = "Ranking, insignias y leaderboard (Sprint 12)")
public class GamificationController {

    private final GamificationService gamificationService;
    private final UserRepository      userRepository;
    private final RolePolicy          rolePolicy;

    /**
     * GET /api/v1/gamification/store/{storeId}/leaderboard?period=weekly|monthly
     * <p>
     * Ranking de colaboradores de una sucursal para el período indicado.
     * Por defecto: {@code weekly}.
     */
    @Operation(summary = "Leaderboard de sucursal",
               description = "ADMIN ve el ranking completo de la sucursal. El GERENTE ve sólo a los ejecutadores a su cargo, y únicamente de su propia sucursal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leaderboard de la sucursal o del equipo"),
            @ApiResponse(responseCode = "403", description = "El GERENTE pidió una sucursal que no es la suya")
    })
    @GetMapping("/store/{storeId}/leaderboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(
            @PathVariable String storeId,
            @RequestParam(defaultValue = "weekly") String period,
            Authentication auth) {
        User current = resolveCurrentUser(auth.getName());

        // El rol por sí solo no acotaba nada: cualquier gerente podía leer el
        // ranking de otra sucursal cambiando el storeId de la URL.
        if (isGerenteOnly(current)) {
            if (!storeId.equals(current.getStoreId())) {
                throw new AccessDeniedException(
                        "El GERENTE solo puede consultar el ranking de su propia sucursal.");
            }
            return ResponseEntity.ok(
                    gamificationService.getTeamLeaderboard(storeId, current.getId(), period));
        }

        return ResponseEntity.ok(gamificationService.getLeaderboard(storeId, period));
    }

    /**
     * GET /api/v1/gamification/gerentes/leaderboard?period=weekly|monthly
     * <p>
     * Ranking gerencial de toda la cadena, con el contexto de equipo de cada gerente.
     * Por defecto: {@code weekly}.
     */
    @Operation(summary = "Leaderboard gerencial",
               description = "Ranking de gerentes de toda la cadena con su sucursal, número de ejecutadores a cargo e IGEO promedio del equipo. Solo ADMIN.")
    @ApiResponse(responseCode = "200", description = "Ranking de gerentes")
    @GetMapping("/gerentes/leaderboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LeaderboardEntryDTO>> getGerencialesLeaderboard(
            @Parameter(description = "Período del ranking: weekly (7 días) o monthly (30 días)")
            @RequestParam(defaultValue = "weekly") String period) {
        return ResponseEntity.ok(gamificationService.getGerencialesLeaderboard(period));
    }

    /**
     * GET /api/v1/gamification/exams
     * Ranking de exámenes filtrado por rol:
     * ADMIN → GERENTEs | GERENTE → EJECUTADOREs de su sucursal | EJECUTADOR → todos en su sucursal.
     */
    @Operation(summary = "Ranking de exámenes por rol",
               description = "ADMIN ve ranking de GERENTEs. GERENTE ve EJECUTADOREs de su sucursal. EJECUTADOR ve todos los participantes de su sucursal.")
    @GetMapping("/exams")
    public ResponseEntity<ExamLeaderboardResponse> getExamLeaderboard(Authentication auth) {
        return ResponseEntity.ok(gamificationService.getExamLeaderboard(auth.getName()));
    }

    /**
     * GET /api/v1/gamification/me
     * <p>
     * Resumen personal de gamificación del usuario autenticado:
     * posición en la sucursal, IGEO acumulado e insignias ganadas.
     */
    @Operation(summary = "Mi resumen de gamificación", description = "Resumen personal del usuario autenticado: posición en sucursal, IGEO acumulado e insignias ganadas.")
    @ApiResponse(responseCode = "200", description = "Resumen de gamificación del usuario")
    @GetMapping("/me")
    public ResponseEntity<GamificationSummaryDTO> getMyGamification(Authentication auth) {
        var user = resolveCurrentUser(auth.getName());
        return ResponseEntity.ok(
                gamificationService.getMyGamification(user.getId(), user.getStoreId()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private User resolveCurrentUser(String numeroUsuario) {
        return userRepository.findByNumeroUsuario(numeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado: " + numeroUsuario));
    }

    private boolean isGerenteOnly(User user) {
        return rolePolicy.isGerenteOnly(user);
    }
}
