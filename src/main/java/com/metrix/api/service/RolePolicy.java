package com.metrix.api.service;

import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import org.springframework.stereotype.Component;

/**
 * Políticas de autorización basadas en roles — fuente única.
 * <p>
 * Reglas canónicas:
 * <ul>
 *   <li>ADMIN  → asigna a GERENTE (cualquier sucursal)</li>
 *   <li>GERENTE → asigna a EJECUTADOR (solo su sucursal)</li>
 *   <li>EJECUTADOR → no asigna</li>
 * </ul>
 */
@Component
public class RolePolicy {

    /**
     * Valida que el creador puede asignar la capacitación al usuario dado
     * en la sucursal indicada. Lanza excepción si la regla se viola.
     */
    public void validateAssignment(User creator, User assignee, String storeId) {
        if (storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("La sucursal de la capacitación es obligatoria.");
        }

        if (!storeId.equals(assignee.getStoreId())) {
            throw new IllegalStateException(
                    "El colaborador asignado no pertenece a la sucursal seleccionada.");
        }

        if (hasRole(creator, Role.ADMIN)) {
            if (!hasRole(assignee, Role.GERENTE)) {
                throw new IllegalStateException(
                        "ADMIN solo puede asignar capacitaciones a gerentes.");
            }
            return;
        }

        if (hasRole(creator, Role.GERENTE)) {
            if (!storeId.equals(creator.getStoreId())) {
                throw new IllegalStateException(
                        "GERENTE solo puede asignar capacitaciones dentro de su sucursal.");
            }
            if (!hasRole(assignee, Role.EJECUTADOR)) {
                throw new IllegalStateException(
                        "GERENTE solo puede asignar capacitaciones a ejecutadores.");
            }
            return;
        }

        throw new IllegalStateException("No tienes permisos para asignar capacitaciones.");
    }

    /**
     * Valida que el usuario actual puede operar (modificar progreso, marcar materiales)
     * sobre la capacitación dada.
     * <ul>
     *   <li>ADMIN: permitido siempre.</li>
     *   <li>GERENTE: permitido solo si la capacitación pertenece a su sucursal.</li>
     *   <li>EJECUTADOR: permitido solo si es el asignado.</li>
     * </ul>
     */
    public void validateOperationScope(User operator, String trainingAssignedUserId,
                                        String trainingStoreId) {
        if (hasRole(operator, Role.ADMIN)) {
            return;
        }

        if (hasRole(operator, Role.GERENTE)) {
            if (!trainingStoreId.equals(operator.getStoreId())) {
                throw new IllegalStateException(
                        "No puedes operar sobre capacitaciones fuera de tu sucursal.");
            }
            return;
        }

        // EJECUTADOR: solo puede operar sobre sus propias capacitaciones
        if (!operator.getId().equals(trainingAssignedUserId)) {
            throw new IllegalStateException(
                    "Solo puedes modificar capacitaciones que te fueron asignadas.");
        }
    }

    /**
     * Retorna el rol objetivo para asignaciones según el rol del creador.
     * Útil para filtrar usuarios en el frontend.
     */
    public Role targetRoleFor(User creator) {
        if (hasRole(creator, Role.ADMIN)) return Role.GERENTE;
        if (hasRole(creator, Role.GERENTE)) return Role.EJECUTADOR;
        return null;
    }

    private boolean hasRole(User user, Role role) {
        return user.getRoles() != null && user.getRoles().contains(role);
    }
}
