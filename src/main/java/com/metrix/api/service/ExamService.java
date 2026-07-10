package com.metrix.api.service;

import com.metrix.api.dto.*;

import java.util.List;

public interface ExamService {

    ExamResponse create(CreateExamRequest request, String creatorNumeroUsuario);

    List<ExamResponse> getByStore(String storeId);

    /** Todos los exámenes activos del sistema (todas las sucursales). Vista del ADMIN. */
    List<ExamResponse> getAll();

    ExamResponse getById(String examId);

    ExamForTakeResponse getForTake(String examId);

    ExamSubmissionResponse submit(String examId, SubmitExamRequest request, String userNumeroUsuario);

    List<ExamSubmissionResponse> getSubmissions(String examId);

    List<ExamSubmissionResponse> getMySubmissions(String userNumeroUsuario);

    ExamResponse createFromTemplate(String templateId, CreateExamFromTemplateRequest request,
                                    String creatorNumeroUsuario);

    AttemptInfoResponse getAttemptInfo(String examId, String userNumeroUsuario);

    ExamStatsResponse getStats(String examId);

    ExamResponse update(String examId, CreateExamRequest request);

    void delete(String examId);

    /** Valida que un GERENTE puede acceder al examen (global o de su sucursal). */
    void assertManagerReadAccess(String examId, String numeroUsuario);

    /** Valida que un GERENTE puede modificar el examen (solo de su sucursal). */
    void assertManagerWriteAccess(String examId, String numeroUsuario);

    /**
     * Backfill administrativo: sincroniza Trainings cuya calificación quedó
     * huérfana por submissions anteriores al fix de sincronización.
     * @return número de Trainings corregidos.
     */
    int reconcileTrainingSync();
}
