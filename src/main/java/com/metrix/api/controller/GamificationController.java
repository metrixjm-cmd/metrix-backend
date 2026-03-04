package com.metrix.api.controller;

import com.metrix.api.dto.GamificationSummaryDTO;
import com.metrix.api.dto.LeaderboardEntryDTO;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.service.GamificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
public class GamificationController {

    private final GamificationService gamificationService;
    private final UserRepository      userRepository;

    /**
     * GET /api/v1/gamification/store/{storeId}/leaderboard?period=weekly|monthly
     * <p>
     * Ranking de colaboradores de una sucursal para el período indicado.
     * Por defecto: {@code weekly}.
     */
    @GetMapping("/store/{storeId}/leaderboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(
            @PathVariable String storeId,
            @RequestParam(defaultValue = "weekly") String period) {
        return ResponseEntity.ok(gamificationService.getLeaderboard(storeId, period));
    }

    /**
     * GET /api/v1/gamification/me
     * <p>
     * Resumen personal de gamificación del usuario autenticado:
     * posición en la sucursal, IGEO acumulado e insignias ganadas.
     */
    @GetMapping("/me")
    public ResponseEntity<GamificationSummaryDTO> getMyGamification(Authentication auth) {
        String numeroUsuario = auth.getName();
        var user = userRepository.findByNumeroUsuario(numeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario autenticado no encontrado: " + numeroUsuario));
        return ResponseEntity.ok(
                gamificationService.getMyGamification(user.getId(), user.getStoreId()));
    }
}
