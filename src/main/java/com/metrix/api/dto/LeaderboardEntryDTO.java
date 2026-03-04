package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Entrada del ranking de gamificación para un colaborador — Sprint 12.
 * <p>
 * Los datos de IGEO se calculan sobre las tareas del período solicitado
 * (weekly: últimos 7 días, monthly: últimos 30 días).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryDTO {

    /** Posición 1-based en el leaderboard del período. */
    private int rank;

    private String userId;
    private String nombre;
    private String puesto;
    private String turno;

    /** IGEO calculado sobre tareas del período. 0.0 si sin datos. */
    private double igeo;

    /** Δ IGEO vs período anterior (positivo = mejora, negativo = baja). 0.0 si sin datos previos. */
    private double igeoChange;

    private int totalTasks;
    private int completedTasks;

    /** On-Time Rate del período. -1.0 si sin datos. */
    private double onTimeRate;

    /** Insignias ganadas (calculadas sobre el historial completo del colaborador). */
    private List<BadgeDTO> badges;
}
