package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Reporte de ranking de colaboradores de una sucursal.
 * <p>
 * Comparte la ventana móvil del reporte gerencial: el ranking se calcula
 * sobre los últimos 7 o 30 días, no sobre un día calendario.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeesReportResponse {

    private String storeId;

    /** Nombre de la sucursal; cae al ID si no se resuelve. */
    private String storeName;

    /** "weekly" (7 días) o "monthly" (30 días). */
    private String period;

    private LocalDate periodStart;
    private LocalDate periodEnd;

    /** Colaboradores ordenados por IGEO descendente, rank 1-based. */
    private List<LeaderboardEntryDTO> employees;

    private int totalEmployees;

    private Instant generatedAt;
}
