package com.metrix.api.service;

import com.metrix.api.dto.GamificationSummaryDTO;
import com.metrix.api.dto.LeaderboardEntryDTO;

import java.util.List;

/**
 * Contrato del módulo de Gamificación — Sprint 12.
 * <p>
 * Calcula insignias y rankings on-the-fly a partir del historial de tareas.
 * No persiste estado de gamificación en MongoDB.
 */
public interface GamificationService {

    /**
     * Ranking del período para una sucursal.
     *
     * @param storeId  ID de la sucursal
     * @param period   "weekly" (7 días) o "monthly" (30 días)
     * @return lista ordenada por IGEO descendente, rank asignado 1-based
     */
    List<LeaderboardEntryDTO> getLeaderboard(String storeId, String period);

    /**
     * Resumen personal de gamificación del usuario autenticado.
     *
     * @param userId   MongoDB ID del usuario
     * @param storeId  sucursal del usuario
     */
    GamificationSummaryDTO getMyGamification(String userId, String storeId);
}
