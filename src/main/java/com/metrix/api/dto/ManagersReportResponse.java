package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Reporte de ranking gerencial de toda la cadena — vista ADMIN.
 * <p>
 * A diferencia del reporte de cierre diario, este no se acota a un día
 * calendario: el ranking gerencial se calcula sobre una ventana móvil
 * (últimos 7 o 30 días desde hoy), así que el reporte lleva la ventana
 * explícita en lugar de una fecha suelta que no describiría su contenido.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagersReportResponse {

    /** "weekly" (7 días) o "monthly" (30 días). */
    private String period;

    /** Primer día de la ventana cubierta. */
    private LocalDate periodStart;

    /** Último día de la ventana cubierta (hoy). */
    private LocalDate periodEnd;

    /** Gerentes ordenados por IGEO de equipo descendente, rank 1-based. */
    private List<LeaderboardEntryDTO> managers;

    private int totalManagers;

    private Instant generatedAt;
}
