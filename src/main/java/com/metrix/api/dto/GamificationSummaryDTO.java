package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Resumen de gamificación personal del usuario autenticado — Sprint 12.
 * <p>
 * Muestra posición en la sucursal, IGEO histórico acumulado e insignias ganadas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamificationSummaryDTO {

    private String userId;
    private String nombre;

    /** Posición 1-based del colaborador en el ranking de la sucursal (IGEO acumulado). */
    private int rank;

    /** Total de colaboradores activos en la sucursal (denominador del ranking). */
    private int totalInStore;

    /** IGEO acumulado histórico del colaborador (sobre todas sus tareas activas). */
    private double igeo;

    /** Insignias obtenidas. */
    private List<BadgeDTO> badges;

    /** Número de insignias ganadas. */
    private int earnedBadgesCount;

    /** Total de insignias disponibles en el sistema. */
    private int availableBadgesCount;
}
