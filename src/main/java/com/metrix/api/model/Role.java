package com.metrix.api.model;

/**
 * Roles del sistema METRIX.
 * <p>
 * ADMIN      → Acceso total: dashboards ejecutivos, configuración de sucursales, gestión de usuarios.
 * GERENTE    → Gestión a nivel sucursal: asignación de tareas, reportes gerenciales, alertas.
 * EJECUTADOR → Operario de campo: recibe tareas, registra evidencia, marca cumplimiento.
 */
public enum Role {
    ADMIN,
    GERENTE,
    EJECUTADOR
}
