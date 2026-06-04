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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

        for (ExamQuestionDto q : request.getQuestions()) {
            if (q.getOptions() == null || q.getOptions().size() < 2) {
                throw new IllegalArgumentException(
                        "La pregunta '" + q.getQuestionText() + "' requiere al menos 2 opciones");
            }
        }

        List<ExamQuestion> questions = request.getQuestions().stream()
                .map(q -> ExamQuestion.builder()
                        .id(UUID.randomUUID().toString())
                        .questionText(q.getQuestionText())
                        .type(q.getType())
                        .options(q.getOptions())
                        .correctOptionIndex(q.getCorrectOptionIndex())
                        .correctOptionIndexes(q.getCorrectOptionIndexes() != null
                                ? q.getCorrectOptionIndexes() : List.of())
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
                .maxAttempts(1)  // regla de negocio: 1 solo intento
                .createdByUserId(creator.getId())
                .createdByName(creator.getNombre())
                .build();

        return toResponse(examRepo.save(exam));
    }

    // ── Consultas ─────────────────────────────────────────────────────────

    @Override
    public List<ExamResponse> getByStore(String storeId) {
        List<Exam> exams = examRepo.findByStoreIdAndActivoTrue(storeId);
        if (exams.isEmpty()) return List.of();
        return exams.stream()
                .map(e -> toResponse(e,
                        submissionRepo.countByExamId(e.getId()),
                        submissionRepo.countByExamIdAndPassedTrue(e.getId())))
                .toList();
    }

    @Override
    public ExamResponse getById(String examId) {
        return toResponse(findExamOrThrow(examId));
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

        if (exam.getMaxAttempts() > 0) {
            long count = submissionRepo.countByExamIdAndUserId(examId, user.getId());
            if (count >= exam.getMaxAttempts()) {
                throw new IllegalStateException("Límite de intentos alcanzado (" + exam.getMaxAttempts() + ")");
            }
        }

        List<ExamQuestion> questions = exam.getQuestions();
        List<ExamAnswer>   answers   = request.getAnswers();

        ExamScoringEngine.ScoringResult scoring = scoringEngine.evaluate(questions, answers);
        boolean passed = scoring.score() >= exam.getPassingScore();

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
                                .correct(r.isCorrect())
                                .pointsEarned(r.getPointsEarned())
                                .pointsMax(r.getPointsMax())
                                .explanation(r.getExplanation())
                                .build())
                        .toList();

        List<SubmissionQuestionResult> persistedResults = questionResults.stream()
                .map(qr -> SubmissionQuestionResult.builder()
                        .questionText(qr.getQuestionText())
                        .type(qr.getType())
                        .options(qr.getOptions())
                        .selectedIndex(qr.getSelectedIndex())
                        .correctIndex(qr.getCorrectIndex())
                        .selectedIndexes(qr.getSelectedIndexes())
                        .correctIndexes(qr.getCorrectIndexes())
                        .correct(qr.isCorrect())
                        .pointsEarned(qr.getPointsEarned())
                        .pointsMax(qr.getPointsMax())
                        .explanation(qr.getExplanation())
                        .build())
                .toList();

        List<String> fraudFlags = new ArrayList<>();
        int timeSecs = request.getTimeTakenSeconds() != null ? request.getTimeTakenSeconds() : 0;
        if (timeSecs > 0 && timeSecs < questions.size() * 2) {
            fraudFlags.add("RESPUESTA_MUY_RAPIDA");
        }
        if (scoring.score() >= 100.0 && submissionRepo.countByExamIdAndUserId(examId, user.getId()) == 0) {
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
                .score(Math.round(scoring.score() * 10.0) / 10.0)
                .passed(passed)
                .timeTakenSeconds(request.getTimeTakenSeconds())
                .submittedAt(Instant.now())
                .questionResults(persistedResults)
                .fraudFlags(fraudFlags)
                .build();

        ExamSubmission saved = submissionRepo.save(submission);
        return toSubmissionResponse(saved, questionResults, exam.getPassingScore());
    }

    // ── Historial de submissions ──────────────────────────────────────────

    @Override
    public List<ExamSubmissionResponse> getSubmissions(String examId) {
        Exam exam = findExamOrThrow(examId);
        return submissionRepo.findByExamIdOrderBySubmittedAtDesc(examId).stream()
                .map(s -> toSubmissionResponse(s, rebuildResults(s), exam.getPassingScore()))
                .toList();
    }

    @Override
    public List<ExamSubmissionResponse> getMySubmissions(String userNumeroUsuario) {
        User user = userRepo.findByNumeroUsuario(userNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userNumeroUsuario));

        List<ExamSubmission> subs = submissionRepo.findByUserIdOrderBySubmittedAtDesc(user.getId());
        if (subs.isEmpty()) return List.of();

        Set<String> examIds = subs.stream().map(ExamSubmission::getExamId).collect(Collectors.toSet());
        Map<String, Integer> passingScores = examRepo.findAllById(examIds).stream()
                .collect(Collectors.toMap(Exam::getId, Exam::getPassingScore, (a, b) -> a));

        return subs.stream()
                .map(s -> toSubmissionResponse(s, rebuildResults(s),
                        passingScores.getOrDefault(s.getExamId(), 70)))
                .toList();
    }

    private List<ExamSubmissionResponse.QuestionResult> rebuildResults(ExamSubmission s) {
        if (s.getQuestionResults() == null || s.getQuestionResults().isEmpty()) return null;
        return s.getQuestionResults().stream()
                .map(r -> ExamSubmissionResponse.QuestionResult.builder()
                        .questionText(r.getQuestionText())
                        .type(r.getType())
                        .options(r.getOptions())
                        .selectedIndex(r.getSelectedIndex())
                        .correctIndex(r.getCorrectIndex())
                        .selectedIndexes(r.getSelectedIndexes())
                        .correctIndexes(r.getCorrectIndexes())
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
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla no encontrada: " + templateId));

        User creator = userRepo.findByNumeroUsuario(creatorNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + creatorNumeroUsuario));

        List<String> questionIds = template.getQuestions().stream()
                .sorted(Comparator.comparingInt(ExamTemplateQuestion::getOrder))
                .map(ExamTemplateQuestion::getQuestionId)
                .toList();

        Map<String, BankQuestion> bankMap = bankQuestionRepo.findAllById(questionIds).stream()
                .collect(Collectors.toMap(BankQuestion::getId, java.util.function.Function.identity()));

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
                .maxAttempts(1)  // regla de negocio: 1 solo intento
                .createdByUserId(creator.getId())
                .createdByName(creator.getNombre())
                .build();

        Exam saved = examRepo.save(exam);

        questionIds.forEach(qId -> bankQuestionRepo.findById(qId).ifPresent(bq -> {
            bq.setUsageCount(bq.getUsageCount() + 1);
            bankQuestionRepo.save(bq);
        }));
        templateService.incrementTimesUsed(templateId);

        return toResponse(saved);
    }

    // ── Estadísticas avanzadas ────────────────────────────────────────────

    @Override
    public ExamStatsResponse getStats(String examId) {
        Exam exam = findExamOrThrow(examId);
        List<ExamSubmission> subs = submissionRepo.findByExamIdOrderBySubmittedAtDesc(examId);

        if (subs.isEmpty()) {
            return ExamStatsResponse.builder()
                    .examId(examId).examTitle(exam.getTitle())
                    .totalSubmissions(0).passedCount(0).passRate(0)
                    .avgScore(0).minScore(0).maxScore(0)
                    .range0_49(scoreRange("0–49", 0, 0))
                    .range50_69(scoreRange("50–69", 0, 0))
                    .range70_89(scoreRange("70–89", 0, 0))
                    .range90_100(scoreRange("90–100", 0, 0))
                    .avgTimeSecs(0).minTimeSecs(-1).maxTimeSecs(-1)
                    .questionFailRates(List.of())
                    .build();
        }

        long   total    = subs.size();
        long   passed   = subs.stream().filter(ExamSubmission::isPassed).count();
        int    passRate = (int) Math.round((passed * 100.0) / total);
        double avgScore = subs.stream().mapToDouble(ExamSubmission::getScore).average().orElse(0);
        double minScore = subs.stream().mapToDouble(ExamSubmission::getScore).min().orElse(0);
        double maxScore = subs.stream().mapToDouble(ExamSubmission::getScore).max().orElse(0);

        long r0  = subs.stream().filter(s -> s.getScore() < 50).count();
        long r50 = subs.stream().filter(s -> s.getScore() >= 50 && s.getScore() < 70).count();
        long r70 = subs.stream().filter(s -> s.getScore() >= 70 && s.getScore() < 90).count();
        long r90 = subs.stream().filter(s -> s.getScore() >= 90).count();

        List<Integer> times = subs.stream()
                .filter(s -> s.getTimeTakenSeconds() != null && s.getTimeTakenSeconds() > 0)
                .map(ExamSubmission::getTimeTakenSeconds).toList();
        double avgTime = times.isEmpty() ? 0 : times.stream().mapToInt(i -> i).average().orElse(0);
        int    minTime = times.isEmpty() ? -1 : times.stream().mapToInt(i -> i).min().orElse(-1);
        int    maxTime = times.isEmpty() ? -1 : times.stream().mapToInt(i -> i).max().orElse(-1);

        int qCount = exam.getQuestions().size();
        List<ExamStatsResponse.QuestionFailRate> failRates = IntStream.range(0, qCount)
                .mapToObj(i -> {
                    String text = exam.getQuestions().get(i).getQuestionText();
                    long subWithResult = subs.stream()
                            .filter(s -> s.getQuestionResults() != null && s.getQuestionResults().size() > i).count();
                    long failCount = subs.stream()
                            .filter(s -> s.getQuestionResults() != null
                                    && s.getQuestionResults().size() > i
                                    && !s.getQuestionResults().get(i).isCorrect()).count();
                    int fr = subWithResult > 0 ? (int) Math.round((failCount * 100.0) / subWithResult) : 0;
                    return ExamStatsResponse.QuestionFailRate.builder()
                            .questionIndex(i).questionText(text)
                            .failCount(failCount).totalCount(subWithResult).failRate(fr)
                            .build();
                })
                .sorted((a, b) -> b.getFailRate() - a.getFailRate())
                .toList();

        return ExamStatsResponse.builder()
                .examId(examId).examTitle(exam.getTitle())
                .totalSubmissions(total).passedCount(passed).passRate(passRate)
                .avgScore(Math.round(avgScore * 10.0) / 10.0)
                .minScore(minScore).maxScore(maxScore)
                .range0_49(scoreRange("0–49", r0, total))
                .range50_69(scoreRange("50–69", r50, total))
                .range70_89(scoreRange("70–89", r70, total))
                .range90_100(scoreRange("90–100", r90, total))
                .avgTimeSecs(Math.round(avgTime * 10.0) / 10.0)
                .minTimeSecs(minTime).maxTimeSecs(maxTime)
                .questionFailRates(failRates)
                .build();
    }

    @Override
    public AttemptInfoResponse getAttemptInfo(String examId, String userNumeroUsuario) {
        Exam exam = findExamOrThrow(examId);
        User user = userRepo.findByNumeroUsuario(userNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + userNumeroUsuario));
        long    count     = submissionRepo.countByExamIdAndUserId(examId, user.getId());
        int     maxAtt    = exam.getMaxAttempts();
        boolean canAtt    = maxAtt == 0 || count < maxAtt;
        long    remaining = maxAtt == 0 ? -1L : maxAtt - count;
        return AttemptInfoResponse.builder()
                .attemptCount(count).maxAttempts(maxAtt)
                .canAttempt(canAtt).remainingAttempts(remaining)
                .build();
    }

    // ── Helpers privados ──────────────────────────────────────────────────

    private Exam findExamOrThrow(String examId) {
        return examRepo.findById(examId)
                .filter(Exam::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado: " + examId));
    }

    private ExamResponse toResponse(Exam exam) {
        return toResponse(exam,
                submissionRepo.countByExamId(exam.getId()),
                submissionRepo.countByExamIdAndPassedTrue(exam.getId()));
    }

    private ExamResponse toResponse(Exam exam, long submissionCount, long passedCount) {
        int passRate = submissionCount > 0
                ? (int) Math.round((passedCount * 100.0) / submissionCount) : 0;

        List<ExamResponse.QuestionDto> questions = exam.getQuestions().stream()
                .map(q -> ExamResponse.QuestionDto.builder()
                        .id(q.getId())
                        .questionText(q.getQuestionText())
                        .type(q.getType())
                        .options(q.getOptions())
                        .correctOptionIndex(q.getCorrectOptionIndex())
                        .correctOptionIndexes(q.getCorrectOptionIndexes())
                        .explanation(q.getExplanation())
                        .points(q.getPoints())
                        .build())
                .toList();

        return ExamResponse.builder()
                .id(exam.getId()).title(exam.getTitle()).description(exam.getDescription())
                .trainingId(exam.getTrainingId()).storeId(exam.getStoreId())
                .questions(questions)
                .passingScore(exam.getPassingScore()).timeLimitMinutes(exam.getTimeLimitMinutes())
                .maxAttempts(exam.getMaxAttempts()).createdByName(exam.getCreatedByName())
                .createdAt(exam.getCreatedAt()).updatedAt(exam.getUpdatedAt())
                .submissionCount(submissionCount).passRate(passRate)
                .build();
    }

    private ExamSubmissionResponse toSubmissionResponse(ExamSubmission s,
                                                         List<ExamSubmissionResponse.QuestionResult> results,
                                                         int passingScore) {
        int ps = passingScore > 0 ? passingScore
                : examRepo.findById(s.getExamId()).map(Exam::getPassingScore).orElse(70);
        return ExamSubmissionResponse.builder()
                .id(s.getId()).examId(s.getExamId()).examTitle(s.getExamTitle())
                .userName(s.getUserName()).userNumero(s.getUserNumero()).storeId(s.getStoreId())
                .score(s.getScore()).passed(s.isPassed()).passingScore(ps)
                .fraudFlags(s.getFraudFlags() != null ? s.getFraudFlags() : List.of())
                .timeTakenSeconds(s.getTimeTakenSeconds()).submittedAt(s.getSubmittedAt())
                .questionResults(results)
                .build();
    }

    private ExamStatsResponse.ScoreRange scoreRange(String label, long count, long total) {
        return ExamStatsResponse.ScoreRange.builder()
                .label(label).count(count)
                .percentage(total > 0 ? (int) Math.round((count * 100.0) / total) : 0)
                .build();
    }
}
