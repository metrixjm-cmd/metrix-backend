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

    ExamResponse createFromTemplate(String templateId, CreateExamFromTemplateRequest request,
                                    String creatorNumeroUsuario);

    AttemptInfoResponse getAttemptInfo(String examId, String userNumeroUsuario);

    ExamStatsResponse getStats(String examId);

    ExamResponse update(String examId, CreateExamRequest request);

    void delete(String examId);
}
