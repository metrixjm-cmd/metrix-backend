package com.metrix.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * KPIs agregados de incidencias de una sucursal (Gestión de Contingencias, Obj. #20).
 * <p>
 * Alimenta el tab "Incidencias" del panel /kpi: gauge de tasa de resolución,
 * donut por estado, distribución por severidad y categoría, y alertas críticas.
 * <p>
 * Convención de sentinel: valores {@code -1.0} indican "sin datos suficientes"
 * (el frontend muestra "S/D"), igual que {@link KpiSummaryResponse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentKpiResponse {

    private String storeId;
    private long   total;

    // ── Donut por estado del ciclo de vida ───────────────────────────────────
    private long abiertas;
    private long enResolucion;
    private long cerradas;

    // ── Gauge principal ──────────────────────────────────────────────────────
    /** % de incidencias cerradas sobre el total. -1.0 si no hay incidencias. */
    private double resolutionRate;

    // ── Alertas ──────────────────────────────────────────────────────────────
    /** Incidencias CRITICA que aún no están CERRADA. */
    private long criticalOpen;

    /** Tiempo medio de resolución en horas (createdAt → resolvedAt). -1.0 si sin datos. */
    private double avgResolutionHours;

    // ── Distribuciones ───────────────────────────────────────────────────────
    /** Conteo por severidad (BAJA, MEDIA, ALTA, CRITICA) en orden fijo. */
    private List<LabelCount> bySeverity;

    /** Conteo por categoría (EQUIPO, INSUMOS, ...) en orden fijo del enum. */
    private List<LabelCount> byCategory;
}
