package com.metrix.api.service;

import com.metrix.api.dto.CreateTaskRequest;
import com.metrix.api.dto.EvidenceUploadResponse;
import com.metrix.api.dto.TaskResponse;
import com.metrix.api.dto.UpdateStatusRequest;

import java.util.List;

/**
 * Contrato del servicio de tareas para METRIX (Sprint 2).
 * <p>
 * Principios SOLID aplicados:
 * <ul>
 *   <li>ISP (Interface Segregation): expone solo los métodos necesarios para
 *       el motor de tareas; consultas de KPIs irán en un servicio separado (Sprint 5).</li>
 *   <li>DIP (Dependency Inversion): {@link com.metrix.api.controller.TaskController}
 *       depende de esta interfaz, nunca de la implementación concreta.</li>
 * </ul>
 */
public interface TaskService {

    /**
     * Crea y persiste una tarea asignada a un colaborador.
     * Solo accesible para ADMIN y GERENTE (validado con @PreAuthorize en el controller).
     *
     * @param request   datos de definición y asignación de la tarea
     * @param createdBy MongoDB _id del usuario creador (extraído del JWT en el controller)
     * @return respuesta con el documento persistido
     */
    TaskResponse createTask(CreateTaskRequest request, String createdBy);

    /**
     * Devuelve todas las tareas activas asignadas a un usuario (cualquier estado).
     * Caso de uso: EJECUTADOR visualiza su portfolio completo.
     *
     * @param assignedUserId MongoDB _id del colaborador
     */
    List<TaskResponse> getTasksByUser(String assignedUserId);

    /**
     * Devuelve las tareas activas de un usuario filtradas por turno.
     * Caso de uso principal del EJECUTADOR (Obj. #1, #7).
     *
     * @param assignedUserId MongoDB _id del colaborador
     * @param shift          turno: MATUTINO | VESPERTINO | NOCTURNO
     */
    List<TaskResponse> getTasksByUserAndShift(String assignedUserId, String shift);

    /**
     * Devuelve todas las tareas activas de una sucursal.
     * Caso de uso: GERENTE monitorea su unidad de trabajo.
     *
     * @param storeId ID de la sucursal
     */
    List<TaskResponse> getTasksByStore(String storeId);

    /**
     * Devuelve las tareas activas de una sucursal filtradas por turno.
     *
     * @param storeId ID de la sucursal
     * @param shift   turno a filtrar
     */
    List<TaskResponse> getTasksByStoreAndShift(String storeId, String shift);

    /**
     * Devuelve el detalle completo de una tarea por su ID.
     *
     * @param taskId MongoDB _id de la tarea
     * @throws com.metrix.api.exception.ResourceNotFoundException si no existe o está inactiva
     */
    TaskResponse getById(String taskId);

    /**
     * Actualiza el estatus de una tarea con timestamps automáticos.
     * <p>
     * Reglas de negocio (ver {@code TaskServiceImpl}):
     * <ul>
     *   <li>PENDING → IN_PROGRESS: registra {@code startedAt = now}.</li>
     *   <li>IN_PROGRESS → COMPLETED: solo si {@code now ≤ dueAt}; calcula {@code onTime=true}.</li>
     *   <li>IN_PROGRESS → FAILED: requiere {@code comments}; {@code onTime=false}; incrementa reworkCount.</li>
     *   <li>COMPLETED|FAILED → cualquier estado: transición ilegal → {@link IllegalStateException}.</li>
     * </ul>
     *
     * @param taskId      MongoDB _id de la tarea
     * @param request     nuevo estado y metadatos opcionales (comments, qualityRating)
     * @param currentUser numeroUsuario del usuario autenticado (subject del JWT)
     */
    TaskResponse updateStatus(String taskId, UpdateStatusRequest request, String currentUser);

    /**
     * Sube un archivo de evidencia a GCS y persiste la URL en el sub-documento
     * {@code execution.evidence} de la tarea.
     * <p>
     * Reglas de negocio:
     * <ul>
     *   <li>La tarea debe estar en estado {@code IN_PROGRESS}.</li>
     *   <li>El usuario autenticado debe ser el colaborador asignado a la tarea.</li>
     * </ul>
     *
     * @param taskId            MongoDB _id de la tarea
     * @param mediaType         "IMAGE" o "VIDEO"
     * @param fileBytes         contenido binario del archivo
     * @param contentType       MIME type del archivo
     * @param extension         extensión sin punto (jpg, mp4, etc.)
     * @param numeroUsuario     subject del JWT del colaborador autenticado
     * @return respuesta con la URL persistida en GCS
     */
    EvidenceUploadResponse addEvidence(String taskId, String mediaType,
                                       byte[] fileBytes, String contentType,
                                       String extension, String numeroUsuario);
}
