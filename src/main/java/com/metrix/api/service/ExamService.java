package com.metrix.api.service;

import com.metrix.api.dto.*;

import java.util.List;

public interface ExamService {

    ExamResponse create(CreateExamRequest request, String creatorNumeroUsuario);

    List<ExamResponse> getByStore(String storeId);

    ExamResponse getById(String examId);

    ExamForTakeResponse getForTake(String examId);

    ExamSubmissionResponse submit(String examId, SubmitExamRequest request, String userNumeroUsuario);

    List<ExamSubmissionResponse> getSubmissions(String examId);

    List<ExamSubmissionResponse> getMySubmissions(String userNumeroUsuario);

    /** Crea un Exam usando una ExamTemplate como base (snapshot de preguntas). */
    ExamResponse createFromTemplate(String templateId, CreateExamFromTemplateRequest request,
                                    String creatorNumeroUsuario);

    /** Información de intentos del usuario actual sobre un examen. */
    AttemptInfoResponse getAttemptInfo(String examId, String userNumeroUsuario);

    /** Revisión manual de respuestas OPEN_TEXT pendientes (ADMIN/GERENTE). */
    ExamSubmissionResponse reviewOpenText(String examId, String submissionId,
                                          ReviewOpenTextRequest request,
                                          String reviewerNumeroUsuario);

    /** Estadísticas agregadas de un examen. */
    ExamStatsResponse getStats(String examId);
}
