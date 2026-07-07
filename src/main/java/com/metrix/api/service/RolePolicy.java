package com.metrix.api.service;

import com.metrix.api.model.ExamAudience;
import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Políticas de autorización basadas en roles — fuente única.
 * <p>
 * Reglas canónicas:
 * <ul>
 *   <li>ADMIN  → asigna a GERENTE o EJECUTADOR (cualquier sucursal)</li>
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
            if (!hasRole(assignee, Role.GERENTE) && !hasRole(assignee, Role.EJECUTADOR)) {
                throw new IllegalStateException(
                        "ADMIN solo puede asignar capacitaciones a gerentes o ejecutadores.");
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
     * Valida la asignación específica de un <b>examen</b> (capacitación con examId).
     * <p>
     * Regla de delegación en dos niveles:
     * <ul>
     *   <li>ADMIN → solo puede asignar exámenes a GERENTES, sin importar el tipo de
     *       examen. Si el examen es para ejecutadores, será el gerente quien lo
     *       reparta a su equipo.</li>
     *   <li>GERENTE → solo puede asignar (redistribuir) exámenes de tipo EJECUTADOR
     *       a sus ejecutadores. Un examen para gerentes no se redistribuye.</li>
     * </ul>
     * Esta validación es adicional a {@link #validateAssignment(User, User, String)},
     * que sigue aplicando para las reglas de sucursal.
     *
     * @param audience público objetivo del examen (puede ser null → se trata como EJECUTADOR).
     */
    public void validateExamAssignment(User creator, User assignee, ExamAudience audience) {
        ExamAudience target = audience != null ? audience : ExamAudience.EJECUTADOR;

        if (hasRole(creator, Role.ADMIN)) {
            if (target == ExamAudience.EJECUTADOR) {
                throw new IllegalStateException(
                        "Los exámenes para ejecutadores los asignan los gerentes a su equipo.");
            }
            if (!hasRole(assignee, Role.GERENTE)) {
                throw new IllegalStateException(
                        "El administrador solo puede asignar exámenes para gerentes.");
            }
            return;
        }

        if (hasRole(creator, Role.GERENTE)) {
            if (target != ExamAudience.EJECUTADOR) {
                throw new IllegalStateException(
                        "Este examen es para gerentes y no puede redistribuirse a ejecutadores.");
            }
            if (!hasRole(assignee, Role.EJECUTADOR)) {
                throw new IllegalStateException(
                        "El gerente solo puede asignar exámenes a ejecutadores.");
            }
            return;
        }

        throw new IllegalStateException("No tienes permisos para asignar exámenes.");
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

    /**
     * Valida que un GERENTE puede asignar una tarea al ejecutador en la sucursal indicada.
     * ADMIN puede asignar a GERENTE en cualquier sucursal; GERENTE solo a su equipo.
     */
    public void validateTaskAssignment(User creator, User assignee, String storeId) {
        if (storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("La sucursal de la tarea es obligatoria.");
        }
        if (!storeId.equals(assignee.getStoreId())) {
            throw new IllegalStateException(
                    "El colaborador asignado no pertenece a la sucursal seleccionada.");
        }

        if (hasRole(creator, Role.ADMIN)) {
            if (!hasRole(assignee, Role.GERENTE)) {
                throw new IllegalStateException("El ADMIN solo puede asignar tareas a un GERENTE.");
            }
            return;
        }

        if (hasRole(creator, Role.GERENTE)) {
            if (!storeId.equals(creator.getStoreId())) {
                throw new AccessDeniedException(
                        "El GERENTE solo puede crear tareas en su propia sucursal.");
            }
            if (!hasRole(assignee, Role.EJECUTADOR)) {
                throw new IllegalStateException("El GERENTE solo puede asignar tareas a un EJECUTADOR.");
            }
            if (!isOwnedByManager(assignee, creator)) {
                throw new AccessDeniedException(
                        "Solo puedes asignar tareas a colaboradores de tu propia plantilla.");
            }
            return;
        }

        throw new AccessDeniedException("No tienes permisos para crear tareas.");
    }

    /** GERENTE (sin rol ADMIN) consultando datos de otra sucursal. */
    public void assertGerenteStoreAccess(User user, String storeId) {
        if (hasRole(user, Role.ADMIN)) {
            return;
        }
        if (isGerenteOnly(user) && (storeId == null || !storeId.equals(user.getStoreId()))) {
            throw new AccessDeniedException(
                    "El GERENTE solo puede consultar datos de su propia sucursal.");
        }
    }

    /**
     * GERENTE puede operar sobre un ejecutador solo si pertenece a su plantilla y sucursal.
     */
    public void assertGerenteCanManageUser(User manager, User target) {
        if (hasRole(manager, Role.ADMIN)) {
            return;
        }
        if (!isGerenteOnly(manager)) {
            throw new AccessDeniedException("Sin permisos para esta operación.");
        }
        if (target.getStoreId() == null || !target.getStoreId().equals(manager.getStoreId())) {
            throw new AccessDeniedException(
                    "El colaborador no pertenece a tu sucursal.");
        }
        if (!hasRole(target, Role.EJECUTADOR) || !isOwnedByManager(target, manager)) {
            throw new AccessDeniedException(
                    "Solo puedes operar sobre colaboradores de tu propia plantilla.");
        }
    }

    /**
     * GERENTE puede leer un examen global (sin store) o de su sucursal.
     */
    public void assertGerenteExamReadAccess(User user, String examStoreId) {
        if (hasRole(user, Role.ADMIN)) {
            return;
        }
        if (examStoreId == null || examStoreId.isBlank()) {
            return;
        }
        assertGerenteStoreAccess(user, examStoreId);
    }

    /**
     * GERENTE solo puede modificar exámenes de su sucursal (no globales).
     */
    public void assertGerenteExamWriteAccess(User user, String examStoreId) {
        if (hasRole(user, Role.ADMIN)) {
            return;
        }
        if (examStoreId == null || examStoreId.isBlank()) {
            throw new AccessDeniedException("Solo ADMIN puede modificar exámenes globales.");
        }
        assertGerenteStoreAccess(user, examStoreId);
    }

    /**
     * @deprecated use {@link #assertGerenteExamReadAccess}
     */
    public void assertGerenteExamAccess(User user, String examStoreId) {
        assertGerenteExamReadAccess(user, examStoreId);
    }

    /**
     * GERENTE puede eliminar evidencias solo de tareas de su equipo en su sucursal.
     */
    public void assertGerenteCanManageTask(User manager, User assignee, String taskStoreId) {
        if (hasRole(manager, Role.ADMIN)) {
            return;
        }
        if (!isGerenteOnly(manager)) {
            throw new AccessDeniedException("Sin permisos para eliminar evidencias.");
        }
        if (taskStoreId == null || !taskStoreId.equals(manager.getStoreId())) {
            throw new AccessDeniedException(
                    "No puedes modificar evidencias de tareas de otra sucursal.");
        }
        if (assignee == null || !isOwnedByManager(assignee, manager)) {
            throw new AccessDeniedException(
                    "Solo puedes eliminar evidencias de tareas de tu propia plantilla.");
        }
    }

    public boolean isGerenteOnly(User user) {
        return hasRole(user, Role.GERENTE) && !hasRole(user, Role.ADMIN);
    }

    public boolean isOwnedByManager(User target, User manager) {
        boolean byId = manager.getId() != null && manager.getId().equals(target.getManagerOwnerId());
        boolean byNumero = manager.getNumeroUsuario() != null
                && manager.getNumeroUsuario().equals(target.getManagerOwnerNumeroUsuario());
        return byId || byNumero;
    }

    private boolean hasRole(User user, Role role) {
        return user.getRoles() != null && user.getRoles().contains(role);
    }
}
