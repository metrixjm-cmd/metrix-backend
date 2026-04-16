package com.metrix.api.service;

import com.metrix.api.model.Catalog;
import com.metrix.api.model.Role;

import java.util.Locale;
import java.util.Set;

/**
 * Reglas de negocio para clasificar y validar puestos por perfil.
 */
public final class PuestoCatalogPolicy {

    public static final String ADMIN_PUESTO = "Administrador";

    private static final Set<String> ADMIN_VALUES = Set.of(
            "ADMINISTRADOR",
            "ADMIN",
            "ADMIN GENERAL"
    );

    private static final Set<String> GERENTE_VALUES = Set.of(
            "GERENTE",
            "GERENTE DE SUCURSAL",
            "GERENTE DE TURNO"
    );

    private PuestoCatalogPolicy() {
    }

    public static Role resolveRole(Catalog catalog) {
        if (catalog.getRole() != null) {
            return catalog.getRole();
        }
        return inferRole(catalog.getValue());
    }

    public static Role inferRole(String puesto) {
        String normalized = normalize(puesto);
        if (ADMIN_VALUES.contains(normalized)) {
            return Role.ADMIN;
        }
        if (GERENTE_VALUES.contains(normalized)) {
            return Role.GERENTE;
        }
        return Role.EJECUTADOR;
    }

    public static String canonicalPuesto(Role role, String puesto) {
        if (role == Role.ADMIN) {
            return ADMIN_PUESTO;
        }
        return puesto != null ? puesto.trim() : "";
    }

    public static boolean isFixedAdminPuesto(String puesto) {
        return ADMIN_VALUES.contains(normalize(puesto));
    }

    private static String normalize(String puesto) {
        return puesto == null ? "" : puesto.trim().toUpperCase(Locale.ROOT);
    }
}
