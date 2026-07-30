package com.metrix.api.service;

import com.metrix.api.dto.ExamLeaderboardResponse;
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
     * Ranking de los ejecutadores a cargo de un gerente — vista GERENTE.
     * <p>
     * Una sucursal puede tener varios gerentes y cada uno responde sólo por su
     * plantilla, así que el ranking se acota por {@code managerOwnerId} en vez de
     * por sucursal completa.
     *
     * @param storeId    sucursal del gerente
     * @param managerId  ID del gerente cuyo equipo se rankea
     * @param period     "weekly" (7 días) o "monthly" (30 días)
     * @return lista ordenada por Over-all descendente, rank asignado 1-based
     */
    List<LeaderboardEntryDTO> getTeamLeaderboard(String storeId, String managerId, String period);

    /**
     * Ranking gerencial de toda la cadena — vista ADMIN.
     * <p>
     * Incluye, además del IGEO propio del gerente, el contexto de su equipo:
     * sucursal, número de ejecutadores a cargo e IGEO promedio del equipo.
     * Ese contexto es lo que permite distinguir a un gerente que ejecuta bien
     * de uno cuyo equipo carga el resultado.
     *
     * @param period "weekly" (7 días) o "monthly" (30 días)
     * @return lista ordenada por IGEO del gerente descendente, rank asignado 1-based
     */
    List<LeaderboardEntryDTO> getGerencialesLeaderboard(String period);

    /**
     * Resumen personal de gamificación del usuario autenticado.
     *
     * @param userId   MongoDB ID del usuario
     * @param storeId  sucursal del usuario
     */
    GamificationSummaryDTO getMyGamification(String userId, String storeId);

    /**
     * Ranking de exámenes filtrado por rol del solicitante.
     * ADMIN → ranking de GERENTEs.
     * GERENTE → ranking de EJECUTADOREs de su sucursal.
     * EJECUTADOR → ranking de todos los participantes de su sucursal.
     */
    ExamLeaderboardResponse getExamLeaderboard(String requesterNumeroUsuario);
}
