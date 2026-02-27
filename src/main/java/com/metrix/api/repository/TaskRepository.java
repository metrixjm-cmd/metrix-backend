package com.metrix.api.repository;

import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repositorio de acceso a datos para la entidad {@link Task}.
 * <p>
 * Los nombres de métodos derivados usan notación de Spring Data con doble
 * guion bajo para navegar sub-documentos MongoDB:
 * {@code Execution_Status} → consulta {@code execution.status} en el documento.
 * <p>
 * Casos de uso cubiertos:
 * <ul>
 *   <li>Portfolio de operario  → {@link #findByAssignedUserIdAndActivoTrue}</li>
 *   <li>Listado EJECUTADOR     → {@link #findByAssignedUserIdAndShiftAndActivoTrue}</li>
 *   <li>Monitor gerencial      → {@link #findByStoreIdAndExecution_StatusAndActivoTrue}</li>
 *   <li>KPI #8 críticas        → {@link #findByCriticalTrueAndExecution_StatusAndActivoTrue}</li>
 *   <li>KPI #1 On-Time Rate    → {@link #countByAssignedUserIdAndExecution_OnTimeTrueAndActivoTrue}</li>
 *   <li>KPI #4 Tiempo ejecución → {@link #findByAssignedUserIdAndExecution_FinishedAtBetween}</li>
 *   <li>Cierre de turno (batch) → {@link #findByDueAtBeforeAndExecution_StatusAndActivoTrue}</li>
 * </ul>
 */
@Repository
public interface TaskRepository extends MongoRepository<Task, String> {

    // ── Portfolio de usuario ─────────────────────────────────────────────

    /** Todas las tareas activas de un usuario (cualquier estado). */
    List<Task> findByAssignedUserIdAndActivoTrue(String assignedUserId);

    /**
     * Tareas activas de un usuario filtradas por turno.
     * Caso de uso principal del EJECUTADOR (Obj. #1, #7).
     */
    List<Task> findByAssignedUserIdAndShiftAndActivoTrue(String assignedUserId, String shift);

    /** Tareas activas de un usuario por estado específico. */
    List<Task> findByAssignedUserIdAndExecution_StatusAndActivoTrue(
            String assignedUserId, TaskStatus status);

    /** Tareas activas de un usuario filtradas por turno y estado. */
    List<Task> findByAssignedUserIdAndShiftAndExecution_StatusAndActivoTrue(
            String assignedUserId, String shift, TaskStatus status);

    // ── Vista gerencial / admin ──────────────────────────────────────────

    /** Todas las tareas activas de una sucursal (vista gerencial). */
    List<Task> findByStoreIdAndActivoTrue(String storeId);

    /**
     * Tareas de una sucursal por estado.
     * Alimenta el monitor de alertas en tiempo real (Obj. #9).
     */
    List<Task> findByStoreIdAndExecution_StatusAndActivoTrue(String storeId, TaskStatus status);

    /** Tareas de una sucursal filtradas por turno. */
    List<Task> findByStoreIdAndShiftAndActivoTrue(String storeId, String shift);

    /** Tareas de una sucursal filtradas por turno y estado. */
    List<Task> findByStoreIdAndShiftAndExecution_StatusAndActivoTrue(
            String storeId, String shift, TaskStatus status);

    // ── KPIs ─────────────────────────────────────────────────────────────

    /**
     * KPI #8: Tareas Críticas No Ejecutadas.
     * Listar tareas marcadas como críticas con estado PENDING o FAILED.
     */
    List<Task> findByCriticalTrueAndExecution_StatusAndActivoTrue(TaskStatus status);

    /**
     * KPI #4: Base para Tiempo Promedio de Ejecución.
     * Tareas de un usuario cerradas dentro de un rango de tiempo.
     */
    List<Task> findByAssignedUserIdAndExecution_FinishedAtBetween(
            String assignedUserId, Instant from, Instant to);

    /**
     * Detección de tareas vencidas para cierre de turno automático (batch).
     * Usado para marcar como FAILED tareas PENDING/IN_PROGRESS después del dueAt.
     */
    List<Task> findByDueAtBeforeAndExecution_StatusAndActivoTrue(Instant now, TaskStatus status);

    /** KPI #6: Ranking Inter-Sucursal. Todas las tareas activas en el sistema. Solo ADMIN. */
    List<Task> findByActivoTrue();

    // ── Conteos para KPIs ────────────────────────────────────────────────

    /** Total de tareas activas de un usuario. Denominador del On-Time Rate (KPI #1). */
    long countByAssignedUserIdAndActivoTrue(String assignedUserId);

    /** Tareas completadas a tiempo de un usuario. Numerador del On-Time Rate (KPI #1). */
    long countByAssignedUserIdAndExecution_OnTimeTrueAndActivoTrue(String assignedUserId);
}
