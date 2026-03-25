package com.metrix.api.service;

import com.metrix.api.dto.*;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.*;
import com.metrix.api.repository.BankQuestionRepository;
import com.metrix.api.repository.ExamRepository;
import com.metrix.api.repository.ExamSubmissionRepository;
import com.metrix.api.repository.ExamTemplateRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Implementación del módulo Trainer — exámenes y calificación automática (Sprint 19).
 * <p>
 * Flujo principal:
 * <ol>
 *   <li>GERENTE/ADMIN crea un examen con preguntas (MC o TF).</li>
 *   <li>Colaborador obtiene {@link ExamForTakeResponse} (sin respuestas correctas).</li>
 *   <li>Colaborador envía sus respuestas → el sistema califica y persiste {@link ExamSubmission}.</li>
 *   <li>GERENTE/ADMIN consulta el historial de submissions.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamRepository           examRepo;
    private final ExamSubmissionRepository submissionRepo;
    private final UserRepository           userRepo;
    private final ExamScoringEngine        scoringEngine;
    private final ExamTemplateRepository   templateRepo;
    private final BankQuestionRepository   bankQuestionRepo;
    private final ExamTemplateService      templateService;

    // ── Crear examen ──────────────────────────────────────────────────────

    @Override
    public ExamResponse create(CreateExamRequest request, String creatorNumeroUsuario) {
        User creator = userRepo.findByNumeroUsuario(creatorNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + creatorNumeroUsuario));

        // Validar opciones por tipo de pregunta
        for (ExamQuestionDto q : request.getQuestions()) {
            QuestionType t = q.getType();
            if (t != QuestionType.OPEN_TEXT) {
                if (q.getOptions() == null || q.getOptions().size() < 2) {
                    throw new IllegalArgumentException(
                            "La pregunta '" + q.getQuestionText() + "' requiere al menos 2 opciones");
                }
            }
        }

        List<ExamQuestion> questions = request.getQuestions().stream()
                .map(q -> ExamQuestion.builder()
                        .id(UUID.randomUUID().toString())
                        .questionText(q.getQuestionText())
                        .type(q.getType())
                        .options(q.getOptions() != null ? q.getOptions() : List.of())
                        .correctOptionIndex(q.getCorrectOptionIndex())
                        .correctOptionIndexes(q.getCorrectOptionIndexes() != null
                                ? q.getCorrectOptionIndexes() : List.of())
                        .acceptedKeywords(q.getAcceptedKeywords() != null
                                ? q.getAcceptedKeywords() : List.of())
                        .explanation(q.getExplanation())
                        .points(q.getPoints() > 0 ? q.getPoints() : 1)
                        .build())
                .toList();

        Exam exam = Exam.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .trainingId(request.getTrainingId())
                .storeId(request.getStoreId())
                .questions(questions)
                .passingScore(request.getPassingScore() > 0 ? request.getPassingScore() : 70)
                .timeLimitMinutes(request.getTimeLimitMinutes())
                .maxAttempts(request.getMaxAttempts())
                .createdByUserId(creator.getId())
                .createdByName(creator.getNombre())
                .build();

        Exam saved = examRepo.save(exam);
        return toResponse(saved);
    }

    // ── Consultas ─────────────────────────────────────────────────────────

    @Override
    public List<ExamResponse> getByStore(String storeId) {
        List<Exam> exams = examRepo.findByStoreIdAndActivoTrue(storeId);
        if (exams.isEmpty()) return List.of();

        // Conteos por exam_id (2 queries por examen, pero con datos mínimos — no carga submissions completas)
        return exams.stream()
                .map(e -> toResponse(e,
                        submissionRepo.countByExamId(e.getId()),
                        submissionRepo.countByExamIdAndPassedTrue(e.getId())))
                .toList();
    }

    @Override
    public ExamResponse getById(String examId) {
        Exam exam = findExamOrThrow(examId);
        return toResponse(exam);
    }

    @Override
    public ExamForTakeResponse getForTake(String examId) {
        Exam exam = findExamOrThrow(examId);

        List<ExamForTakeResponse.QuestionForTake> questions = exam.getQuestions().stream()
                .map(q -> ExamForTakeResponse.QuestionForTake.builder()
                        .id(q.getId())
                        .questionText(q.getQuestionText())
                        .type(q.getType())
                        .options(q.getOptions())
                        .points(q.getPoints())
                        .build())
                .toList();

        return ExamForTakeResponse.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .passingScore(exam.getPassingScore())
                .timeLimitMinutes(exam.getTimeLimitMinutes())
                .maxAttempts(exam.getMaxAttempts())
                .questionCount(exam.getQuestions().size())
                .questions(questions)
                .build();
    }

    // ── Enviar respuestas y calificar ─────────────────────────────────────

    @Override
    public ExamSubmissionResponse submit(String examId, SubmitExamRequest request, String userNumeroUsuario) {
        Exam exam = findExamOrThrow(examId);
        User user = userRepo.findByNumeroUsuario(userNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userNumeroUsuario));

        // ── Validar límite de intentos ────────────────────────────────────
        if (exam.getMaxAttempts() > 0) {
            long count = submissionRepo.countByExamIdAndUserId(examId, user.getId());
            if (count >= exam.getMaxAttempts()) {
                throw new IllegalStateException(
                        "Límite de intentos alcanzado (" + exam.getMaxAttempts() + ")");
            }
        }

        List<ExamQuestion> questions = exam.getQuestions();
        List<ExamAnswer>   answers   = request.getAnswers();

        // ── Calificación automática vía ExamScoringEngine ─────────────────
        ExamScoringEngine.ScoringResult scoring = scoringEngine.evaluate(questions, answers);
        double score  = scoring.score();
        boolean passed = score >= exam.getPassingScore();

        List<ExamSubmissionResponse.QuestionResult> questionResults =
                scoring.questionResults().stream()
                        .map(r -> ExamSubmissionResponse.QuestionResult.builder()
                                .questionText(r.getQuestionText())
                                .type(r.getType())
                                .options(r.getOptions())
                                .selectedIndex(r.getSelectedIndex())
                                .correctIndex(r.getCorrectIndex())
                                .selectedIndexes(r.getSelectedIndexes())
                                .correctIndexes(r.getCorrectIndexes())
                                .textAnswer(r.getTextAnswer())
                                .acceptedKeywords(r.getAcceptedKeywords())
                                .pendingReview(r.isPendingReview())
                                .correct(r.isCorrect())
                                .pointsEarned(r.getPointsEarned())
                                .pointsMax(r.getPointsMax())
                                .explanation(r.getExplanation())
                                .build())
                        .toList();

        // ── Persistir submission — incluye todos los campos para revisión/historial ─
        List<SubmissionQuestionResult> persistedResults = questionResults.stream()
                .map(qr -> SubmissionQuestionResult.builder()
                        .questionText(qr.getQuestionText())
                        .type(qr.getType())
                        .options(qr.getOptions())
                        .selectedIndex(qr.getSelectedIndex())
                        .correctIndex(qr.getCorrectIndex())
                        .selectedIndexes(qr.getSelectedIndexes())
                        .correctIndexes(qr.getCorrectIndexes())
                        .textAnswer(qr.getTextAnswer())
                        .acceptedKeywords(qr.getAcceptedKeywords())
                        .pendingReview(qr.isPendingReview())
                        .correct(qr.isCorrect())
                        .pointsEarned(qr.getPointsEarned())
                        .pointsMax(qr.getPointsMax())
                        .explanation(qr.getExplanation())
                        .build())
                .toList();

        // ── Detección de fraude ──────────────────────────────────────────────
        List<String> fraudFlags = new ArrayList<>();
        int timeSecs = request.getTimeTakenSeconds() != null ? request.getTimeTakenSeconds() : 0;
        int minExpectedSecs = questions.size() * 2;
        if (timeSecs > 0 && timeSecs < minExpectedSecs) {
            fraudFlags.add("RESPUESTA_MUY_RAPIDA");
        }
        if (score >= 100.0 && submissionRepo.countByExamIdAndUserId(examId, user.getId()) == 0) {
            fraudFlags.add("PUNTAJE_PERFECTO_PRIMER_INTENTO");
        }

        ExamSubmission submission = ExamSubmission.builder()
                .examId(exam.getId())
                .examTitle(exam.getTitle())
                .userId(user.getId())
                .userName(user.getNombre())
                .userNumero(user.getNumeroUsuario())
                .storeId(exam.getStoreId())
                .detailedAnswers(answers)
                .score(Math.round(score * 10.0) / 10.0)
                .passed(passed)
                .timeTakenSeconds(request.getTimeTakenSeconds())
                .submittedAt(Instant.now())
                .questionResults(persistedResults)
                .fraudFlags(fraudFlags)
                .build();

        ExamSubmission saved = submissionRepo.save(submission);

        return toSubmissionResponse(saved, questionResults, exam.getPassingScore(),
                scoring.hasPendingReview());
    }

    // ── Historial de submissions ──────────────────────────────────────────

    @Override
    public List<ExamSubmissionResponse> getSubmissions(String examId) {
        Exam exam = findExamOrThrow(examId);
        int ps = exam.getPassingScore();
        return submissionRepo.findByExamIdOrderBySubmittedAtDesc(examId).stream()
                .map(s -> toSubmissionResponse(s, rebuildResults(s), ps))
                .toList();
    }

    @Override
    public List<ExamSubmissionResponse> getMySubmissions(String userNumeroUsuario) {
        User user = userRepo.findByNumeroUsuario(userNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userNumeroUsuario));

        List<ExamSubmission> subs = submissionRepo.findByUserIdOrderBySubmittedAtDesc(user.getId());
        if (subs.isEmpty()) return List.of();

        // Batch-load passing scores to avoid N+1
        Set<String> examIds = subs.stream().map(ExamSubmission::getExamId).collect(Collectors.toSet());
        Map<String, Integer> passingScores = examRepo.findAllById(examIds).stream()
                .collect(Collectors.toMap(Exam::getId, Exam::getPassingScore, (a, b) -> a));

        return subs.stream()
                .map(s -> toSubmissionResponse(s, rebuildResults(s),
                        passingScores.getOrDefault(s.getExamId(), 70)))
                .toList();
    }

    /** Reconstruye QuestionResult[] desde los datos persistidos en la submission. */
    private List<ExamSubmissionResponse.QuestionResult> rebuildResults(ExamSubmission s) {
        if (s.getQuestionResults() == null || s.getQuestionResults().isEmpty()) {
            return null; // submissions pre-E1 sin datos persistidos
        }
        return s.getQuestionResults().stream()
                .map(r -> ExamSubmissionResponse.QuestionResult.builder()
                        .questionText(r.getQuestionText())
                        .type(r.getType())
                        .options(r.getOptions())
                        .selectedIndex(r.getSelectedIndex())
                        .correctIndex(r.getCorrectIndex())
                        .selectedIndexes(r.getSelectedIndexes())
                        .correctIndexes(r.getCorrectIndexes())
                        .textAnswer(r.getTextAnswer())
                        .acceptedKeywords(r.getAcceptedKeywords())
                        .pendingReview(r.isPendingReview())
                        .correct(r.isCorrect())
                        .pointsEarned(r.getPointsEarned())
                        .pointsMax(r.getPointsMax())
                        .explanation(r.getExplanation())
                        .build())
                .toList();
    }

    // ── Crear desde plantilla ─────────────────────────────────────────────

    @Override
    public ExamResponse createFromTemplate(String templateId,
                                            CreateExamFromTemplateRequest request,
                                            String creatorNumeroUsuario) {
        ExamTemplate template = templateRepo.findById(templateId)
                .filter(ExamTemplate::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Plantilla de examen no encontrada: " + templateId));

        User creator = userRepo.findByNumeroUsuario(creatorNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + creatorNumeroUsuario));

        // Resolver preguntas del banco en 1 query — snapshot inmutable
        List<String> questionIds = template.getQuestions().stream()
                .sorted(Comparator.comparingInt(ExamTemplateQuestion::getOrder))
                .map(ExamTemplateQuestion::getQuestionId)
                .toList();

        Map<String, BankQuestion> bankMap = bankQuestionRepo.findAllById(questionIds).stream()
                .collect(java.util.stream.Collectors.toMap(BankQuestion::getId,
                        java.util.function.Function.identity()));

        List<ExamQuestion> examQuestions = template.getQuestions().stream()
                .sorted(Comparator.comparingInt(ExamTemplateQuestion::getOrder))
                .map(tq -> {
                    BankQuestion bq = bankMap.get(tq.getQuestionId());
                    if (bq == null) throw new ResourceNotFoundException(
                            "Pregunta del banco no encontrada: " + tq.getQuestionId());
                    int points = tq.getPointsOverride() > 0 ? tq.getPointsOverride() : bq.getPoints();
                    return ExamQuestion.builder()
                            .id(UUID.randomUUID().toString())
                            .questionText(bq.getQuestionText())
                            .type(bq.getType())
                            .options(bq.getOptions())
                            .correctOptionIndex(bq.getCorrectOptionIndex())
                            .correctOptionIndexes(bq.getCorrectOptionIndexes())
                            .acceptedKeywords(bq.getAcceptedKeywords())
                            .explanation(bq.getExplanation())
                            .points(points)
                            .build();
                })
                .toList();

        int passingScore  = request.getPassingScoreOverride() > 0
                ? request.getPassingScoreOverride() : template.getPassingScore();
        Integer timeLimit = request.getTimeLimitOverride() > 0
                ? request.getTimeLimitOverride() : template.getTimeLimitMinutes();

        Exam exam = Exam.builder()
                .title(template.getTitle())
                .description(template.getDescription())
                .storeId(request.getStoreId())
                .questions(new ArrayList<>(examQuestions))
                .passingScore(passingScore)
                .timeLimitMinutes(timeLimit)
                .maxAttempts(template.getMaxAttempts())
                .createdByUserId(creator.getId())
                .createdByName(creator.getNombre())
                .build();

        Exam saved = examRepo.save(exam);

        // Incrementar usageCount de cada pregunta del banco
        questionIds.forEach(qId -> bankQuestionRepo.findById(qId).ifPresent(bq -> {
            bq.setUsageCount(bq.getUsageCount() + 1);
            bankQuestionRepo.save(bq);
        }));

        // Incrementar timesUsed en la plantilla
        templateService.incrementTimesUsed(templateId);

        return toResponse(saved);
    }

    // ── Helpers privados ──────────────────────────────────────────────────

    private Exam findExamOrThrow(String examId) {
        return examRepo.findById(examId)
                .filter(Exam::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado: " + examId));
    }

    /** Single exam detail — 2 count queries (acceptable for detail view). */
    private ExamResponse toResponse(Exam exam) {
        return toResponse(exam,
                submissionRepo.countByExamId(exam.getId()),
                submissionRepo.countByExamIdAndPassedTrue(exam.getId()));
    }

    /** Batch-friendly with pre-computed counts (eliminates N+1 in getByStore). */
    private ExamResponse toResponse(Exam exam, long submissionCount, long passedCount) {
        int passRate = submissionCount > 0 ? (int) Math.round((passedCount * 100.0) / submissionCount) : 0;

        List<ExamResponse.QuestionDto> questions = exam.getQuestions().stream()
                .map(q -> ExamResponse.QuestionDto.builder()
                        .id(q.getId())
                        .questionText(q.getQuestionText())
                        .type(q.getType())
                        .options(q.getOptions())
                        .correctOptionIndex(q.getCorrectOptionIndex())
                        .correctOptionIndexes(q.getCorrectOptionIndexes())
                        .acceptedKeywords(q.getAcceptedKeywords())
                        .explanation(q.getExplanation())
                        .points(q.getPoints())
                        .build())
                .toList();

        return ExamResponse.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .trainingId(exam.getTrainingId())
                .storeId(exam.getStoreId())
                .questions(questions)
                .passingScore(exam.getPassingScore())
                .timeLimitMinutes(exam.getTimeLimitMinutes())
                .maxAttempts(exam.getMaxAttempts())
                .createdByName(exam.getCreatedByName())
                .createdAt(exam.getCreatedAt())
                .updatedAt(exam.getUpdatedAt())
                .submissionCount(submissionCount)
                .passRate(passRate)
                .build();
    }

    // ── Revisión manual OPEN_TEXT ─────────────────────────────────────────

    @Override
    public ExamSubmissionResponse reviewOpenText(String examId, String submissionId,
                                                  ReviewOpenTextRequest request,
                                                  String reviewerNumeroUsuario) {
        findExamOrThrow(examId); // valida que el examen existe y está activo
        ExamSubmission sub = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission no encontrada: " + submissionId));

        List<SubmissionQuestionResult> results = sub.getQuestionResults();
        if (results == null || results.isEmpty()) {
            throw new IllegalStateException("Esta submission no tiene desglose de preguntas.");
        }

        // Aplicar overrides
        for (ReviewOpenTextRequest.ReviewItem item : request.getReviews()) {
            int idx = item.getQuestionIndex();
            if (idx < 0 || idx >= results.size()) continue;
            SubmissionQuestionResult qr = results.get(idx);
            qr.setReviewOverride(item.isApproved());
            qr.setCorrect(item.isApproved());
            qr.setPointsEarned(item.isApproved() ? qr.getPointsMax() : 0.0);
            qr.setPendingReview(false);
        }

        // Recalcular score total
        double totalPoints    = results.stream().mapToDouble(SubmissionQuestionResult::getPointsMax).sum();
        double earnedPoints   = results.stream().mapToDouble(SubmissionQuestionResult::getPointsEarned).sum();
        double newScore       = totalPoints > 0 ? Math.round((earnedPoints / totalPoints) * 1000.0) / 10.0 : 0;

        Exam exam = findExamOrThrow(examId);
        boolean newPassed = newScore >= exam.getPassingScore();

        sub.setScore(newScore);
        sub.setPassed(newPassed);
        sub.setReviewed(true);
        sub.setQuestionResults(results);

        ExamSubmission saved = submissionRepo.save(sub);
        return toSubmissionResponse(saved, rebuildResults(saved), exam.getPassingScore());
    }

    // ── Estadísticas avanzadas ────────────────────────────────────────────

    @Override
    public ExamStatsResponse getStats(String examId) {
        Exam exam = findExamOrThrow(examId);
        List<ExamSubmission> subs = submissionRepo.findByExamIdOrderBySubmittedAtDesc(examId);

        if (subs.isEmpty()) {
            return ExamStatsResponse.builder()
                    .examId(examId)
                    .examTitle(exam.getTitle())
                    .totalSubmissions(0)
                    .passedCount(0)
                    .passRate(0)
                    .avgScore(0)
                    .minScore(0)
                    .maxScore(0)
                    .range0_49(ExamStatsResponse.ScoreRange.builder().label("0–49").count(0).percentage(0).build())
                    .range50_69(ExamStatsResponse.ScoreRange.builder().label("50–69").count(0).percentage(0).build())
                    .range70_89(ExamStatsResponse.ScoreRange.builder().label("70–89").count(0).percentage(0).build())
                    .range90_100(ExamStatsResponse.ScoreRange.builder().label("90–100").count(0).percentage(0).build())
                    .avgTimeSecs(0)
                    .minTimeSecs(-1)
                    .maxTimeSecs(-1)
                    .pendingReviewCount(0)
                    .questionFailRates(List.of())
                    .build();
        }

        long total   = subs.size();
        long passed  = subs.stream().filter(ExamSubmission::isPassed).count();
        int  passRate = (int) Math.round((passed * 100.0) / total);

        double avgScore = subs.stream().mapToDouble(ExamSubmission::getScore).average().orElse(0);
        double minScore = subs.stream().mapToDouble(ExamSubmission::getScore).min().orElse(0);
        double maxScore = subs.stream().mapToDouble(ExamSubmission::getScore).max().orElse(0);

        // Distribución
        long r0   = subs.stream().filter(s -> s.getScore() < 50).count();
        long r50  = subs.stream().filter(s -> s.getScore() >= 50 && s.getScore() < 70).count();
        long r70  = subs.stream().filter(s -> s.getScore() >= 70 && s.getScore() < 90).count();
        long r90  = subs.stream().filter(s -> s.getScore() >= 90).count();

        // Tiempos
        List<Integer> times = subs.stream()
                .filter(s -> s.getTimeTakenSeconds() != null && s.getTimeTakenSeconds() > 0)
                .map(ExamSubmission::getTimeTakenSeconds).toList();
        double avgTime = times.isEmpty() ? 0 : times.stream().mapToInt(i -> i).average().orElse(0);
        int    minTime = times.isEmpty() ? -1 : times.stream().mapToInt(i -> i).min().orElse(-1);
        int    maxTime = times.isEmpty() ? -1 : times.stream().mapToInt(i -> i).max().orElse(-1);

        // Pending review
        long pendingCount = subs.stream()
                .filter(s -> s.getQuestionResults() != null &&
                        s.getQuestionResults().stream()
                                .anyMatch(qr -> qr.isPendingReview() && qr.getReviewOverride() == null))
                .count();

        // Tasa de fallo por pregunta
        int qCount = exam.getQuestions().size();
        List<ExamStatsResponse.QuestionFailRate> failRates = IntStream.range(0, qCount)
                .mapToObj(i -> {
                    String text = exam.getQuestions().get(i).getQuestionText();
                    long subWithResult = subs.stream()
                            .filter(s -> s.getQuestionResults() != null && s.getQuestionResults().size() > i)
                            .count();
                    long failCount = subs.stream()
                            .filter(s -> s.getQuestionResults() != null
                                    && s.getQuestionResults().size() > i
                                    && !s.getQuestionResults().get(i).isCorrect())
                            .count();
                    int fr = subWithResult > 0 ? (int) Math.round((failCount * 100.0) / subWithResult) : 0;
                    return ExamStatsResponse.QuestionFailRate.builder()
                            .questionIndex(i)
                            .questionText(text)
                            .failCount(failCount)
                            .totalCount(subWithResult)
                            .failRate(fr)
                            .build();
                })
                .sorted((a, b) -> b.getFailRate() - a.getFailRate())
                .toList();

        return ExamStatsResponse.builder()
                .examId(examId)
                .examTitle(exam.getTitle())
                .totalSubmissions(total)
                .passedCount(passed)
                .passRate(passRate)
                .avgScore(Math.round(avgScore * 10.0) / 10.0)
                .minScore(minScore)
                .maxScore(maxScore)
                .range0_49(scoreRange("0–49", r0, total))
                .range50_69(scoreRange("50–69", r50, total))
                .range70_89(scoreRange("70–89", r70, total))
                .range90_100(scoreRange("90–100", r90, total))
                .avgTimeSecs(Math.round(avgTime * 10.0) / 10.0)
                .minTimeSecs(minTime)
                .maxTimeSecs(maxTime)
                .pendingReviewCount(pendingCount)
                .questionFailRates(failRates)
                .build();
    }

    private ExamStatsResponse.ScoreRange scoreRange(String label, long count, long total) {
        return ExamStatsResponse.ScoreRange.builder()
                .label(label)
                .count(count)
                .percentage(total > 0 ? (int) Math.round((count * 100.0) / total) : 0)
                .build();
    }

    @Override
    public AttemptInfoResponse getAttemptInfo(String examId, String userNumeroUsuario) {
        Exam exam = findExamOrThrow(examId);
        User user = userRepo.findByNumeroUsuario(userNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userNumeroUsuario));

        long count     = submissionRepo.countByExamIdAndUserId(examId, user.getId());
        int  maxAtt    = exam.getMaxAttempts();
        boolean canAtt = maxAtt == 0 || count < maxAtt;
        long remaining = maxAtt == 0 ? -1L : maxAtt - count;

        return AttemptInfoResponse.builder()
                .attemptCount(count)
                .maxAttempts(maxAtt)
                .canAttempt(canAtt)
                .remainingAttempts(remaining)
                .build();
    }

    private ExamSubmissionResponse toSubmissionResponse(ExamSubmission s,
                                                         List<ExamSubmissionResponse.QuestionResult> results,
                                                         int passingScore) {
        return toSubmissionResponse(s, results, passingScore, false);
    }

    private ExamSubmissionResponse toSubmissionResponse(ExamSubmission s,
                                                         List<ExamSubmissionResponse.QuestionResult> results,
                                                         int passingScore,
                                                         boolean hasPendingReview) {
        int ps = passingScore > 0 ? passingScore
                : examRepo.findById(s.getExamId()).map(Exam::getPassingScore).orElse(70);

        boolean pendingReview = hasPendingReview ||
                (s.getQuestionResults() != null && s.getQuestionResults().stream()
                        .anyMatch(qr -> qr.isPendingReview() && qr.getReviewOverride() == null));

        return ExamSubmissionResponse.builder()
                .id(s.getId())
                .examId(s.getExamId())
                .examTitle(s.getExamTitle())
                .userName(s.getUserName())
                .userNumero(s.getUserNumero())
                .storeId(s.getStoreId())
                .score(s.getScore())
                .passed(s.isPassed())
                .passingScore(ps)
                .hasPendingReview(pendingReview)
                .reviewed(s.isReviewed())
                .fraudFlags(s.getFraudFlags() != null ? s.getFraudFlags() : List.of())
                .timeTakenSeconds(s.getTimeTakenSeconds())
                .submittedAt(s.getSubmittedAt())
                .questionResults(results)
                .build();
    }
}
