package com.metrix.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta de la API de sucursales — Sprint 11.
 * Incluye stats denormalizados calculados al vuelo (conteos de usuarios, tareas y capacitaciones).
 */
@Data
@Builder
public class StoreResponse {

    // ── Datos de la sucursal ──────────────────────────────────────────────
    private String id;
    private String nombre;
    private String codigo;
    private String direccion;
    private String telefono;
    private List<String> turnos;
    private boolean activo;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    // ── Estadísticas calculadas ───────────────────────────────────────────
    /** Colaboradores activos en la sucursal. */
    private long totalUsers;

    /** Tareas activas en la sucursal. */
    private long totalTasks;

    /** Capacitaciones activas en la sucursal. */
    private long totalTrainings;
}
