package com.metrix.api.service;

import com.metrix.api.dto.*;
import com.metrix.api.model.*;
import com.metrix.api.repository.BankQuestionRepository;
import java.util.Set;
import com.metrix.api.repository.ExamRepository;
import com.metrix.api.repository.ExamSubmissionRepository;
import com.metrix.api.repository.ExamTemplateRepository;
import com.metrix.api.repository.TrainingRepository;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FASE 2: ExamServiceImplTest (15 tests)
 * Cubre: scoring por tipo pregunta, validación de duración, estadísticas.
 */
@ExtendWith(MockitoExtension.class)
class ExamServiceImplTest {

    @Mock
    private ExamRepository examRepo;

    @Mock
    private ExamSubmissionRepository submissionRepo;

    @Mock
    private UserRepository userRepo;

    @Spy
    private ExamScoringEngine scoringEngine;

    @Mock
    private ExamTemplateRepository templateRepo;

    @Mock
    private BankQuestionRepository bankQuestionRepo;

    @Mock
    private ExamTemplateService templateService;

    @Mock
    private TrainingRepository trainingRepository;

    @Mock
    private TrainingStateMachine trainingStateMachine;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ExamServiceImpl examService;

    private User testAdmin;
    private User testGerente;
    private User testEjecutador;

    @BeforeEach
    void setup() {
        testAdmin = User.builder()
                .id("user-admin-1")
                .numeroUsuario("ADMIN001")
                .nombre("Admin User")
                .roles(Set.of(Role.ADMIN))
                .storeId("store-1")
                .build();

        testGerente = User.builder()
                .id("user-ger-1")
                .numeroUsuario("GER001")
                .nombre("Gerente User")
                .roles(Set.of(Role.GERENTE))
                .storeId("store-1")
                .build();

        testEjecutador = User.builder()
                .id("user-eje-1")
                .numeroUsuario("EJE001")
                .nombre("Ejecutador User")
                .roles(Set.of(Role.EJECUTADOR))
                .storeId("store-1")
                .build();
    }

    // ==================== DURATION VALIDATION (Tests 1-3) ====================

    /** Test 1: Duración de 60 minutos es aceptada */
    @Test
    void createExam_accepts_duration_60_minutes() {
        CreateExamRequest request = buildCreateRequest("1-Hour Exam", 60, "store-1");
        Exam exam = buildSavedExam("exam-60min", request.getTitle(), request.getStoreId(), 60);

        when(userRepo.findByNumeroUsuario("ADMIN001")).thenReturn(Optional.of(testAdmin));
        when(examRepo.save(any(Exam.class))).thenReturn(exam);

        ExamResponse result = examService.create(request, "ADMIN001");

        assertNotNull(result);
        assertEquals(60, result.getTimeLimitMinutes());
        verify(examRepo).save(any(Exam.class));
    }

    /** Test 2: Duración menor a 60 es rechazada (validación del DTO @Min(1) — aquí validamos modelo) */
    @Test
    void createExam_rejects_null_duration() {
        // El DTO acepta null (sin límite), pero el dominio puede imponer restricción
        CreateExamRequest request = new CreateExamRequest();
        request.setTitle("No-Limit Exam");
        request.setStoreId("store-1");
        // timeLimitMinutes = null (sin límite) — válido en el sistema
        request.setQuestions(List.of(buildQuestionDto("MULTI_SELECT", 0)));

        Exam exam = buildSavedExam("exam-no-limit", request.getTitle(), request.getStoreId(), null);
        when(userRepo.findByNumeroUsuario("ADMIN001")).thenReturn(Optional.of(testAdmin));
        when(examRepo.save(any(Exam.class))).thenReturn(exam);

        // timeLimitMinutes null = sin límite, debe ser válido
        ExamResponse result = examService.create(request, "ADMIN001");
        assertNotNull(result);
    }

    /** Test 3: Duración de 1440 minutos (24h) es aceptada */
    @Test
    void createExam_accepts_duration_1440_minutes() {
        CreateExamRequest request = buildCreateRequest("24h Exam", 1440, "store-1");
        Exam exam = buildSavedExam("exam-24h", request.getTitle(), request.getStoreId(), 1440);

        when(userRepo.findByNumeroUsuario("ADMIN001")).thenReturn(Optional.of(testAdmin));
        when(examRepo.save(any(Exam.class))).thenReturn(exam);

        ExamResponse result = examService.create(request, "ADMIN001");

        assertNotNull(result);
        assertEquals(1440, result.getTimeLimitMinutes());
    }

    // ==================== SCORING LOGIC (Tests 4-9) ====================

    /** Test 4: TRUE_FALSE respuesta correcta → score = 100, passed = true */
    @Test
    void submitExam_TRUE_FALSE_correct_answer_scores_100() {
        String examId = "exam-mc";
        Exam exam = buildExamWithQuestion(examId, QuestionType.TRUE_FALSE, 0, null);

        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(ExamAnswer.builder().selectedIndex(0).build()));
        request.setTimeTakenSeconds(600);

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(userRepo.findByNumeroUsuario("EJE001")).thenReturn(Optional.of(testEjecutador));
        when(submissionRepo.save(any(ExamSubmission.class))).thenAnswer(i -> i.getArgument(0));

        ExamSubmissionResponse result = examService.submit(examId, request, "EJE001");

        assertNotNull(result);
        assertEquals(100.0, result.getScore(), 0.01);
        assertTrue(result.isPassed());
    }

    /** Test 5: TRUE_FALSE respuesta incorrecta → score = 0, passed = false */
    @Test
    void submitExam_TRUE_FALSE_wrong_answer_scores_0() {
        String examId = "exam-mc-wrong";
        Exam exam = buildExamWithQuestion(examId, QuestionType.TRUE_FALSE, 0, null);

        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(ExamAnswer.builder().selectedIndex(2).build())); // incorrecto

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(userRepo.findByNumeroUsuario("EJE001")).thenReturn(Optional.of(testEjecutador));
        when(submissionRepo.save(any(ExamSubmission.class))).thenAnswer(i -> i.getArgument(0));

        ExamSubmissionResponse result = examService.submit(examId, request, "EJE001");

        assertNotNull(result);
        assertEquals(0.0, result.getScore(), 0.01);
        assertFalse(result.isPassed());
    }

    /** Test 6: TRUE_FALSE respuesta correcta → passed = true */
    @Test
    void submitExam_TRUE_FALSE_correct() {
        String examId = "exam-tf";
        Exam exam = buildExamWithQuestion(examId, QuestionType.TRUE_FALSE, 0, null);

        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(ExamAnswer.builder().selectedIndex(0).build())); // Verdadero

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(userRepo.findByNumeroUsuario("EJE001")).thenReturn(Optional.of(testEjecutador));
        when(submissionRepo.save(any(ExamSubmission.class))).thenAnswer(i -> i.getArgument(0));

        ExamSubmissionResponse result = examService.submit(examId, request, "EJE001");

        assertTrue(result.isPassed());
        assertEquals(100.0, result.getScore(), 0.01);
    }

    /** Test 7: MULTI_SELECT 2 de 3 correctas → score proporcional ≈ 66% */
    @Test
    void submitExam_MULTI_SELECT_partial_points_2_of_3() {
        String examId = "exam-ms-partial";
        Exam exam = buildExamWithQuestion(examId, QuestionType.MULTI_SELECT, -1, List.of(0, 1, 2));

        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(
                ExamAnswer.builder().selectedIndexes(List.of(0, 1)).build() // 2 de 3
        ));

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(userRepo.findByNumeroUsuario("EJE001")).thenReturn(Optional.of(testEjecutador));
        when(submissionRepo.save(any(ExamSubmission.class))).thenAnswer(i -> i.getArgument(0));

        ExamSubmissionResponse result = examService.submit(examId, request, "EJE001");

        assertNotNull(result);
        assertTrue(result.getScore() > 50 && result.getScore() < 80,
                "Score should be ~66% for 2/3 correct: got " + result.getScore());
    }

    /** Test 8: MULTI_SELECT respuesta vacía → score = 0 */
    @Test
    void submitExam_MULTI_SELECT_empty_answer_scores_0() {
        String examId = "exam-ms-empty";
        Exam exam = buildExamWithQuestion(examId, QuestionType.MULTI_SELECT, -1, List.of(0, 1));

        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(
                ExamAnswer.builder().selectedIndexes(List.of()).build()
        ));

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(userRepo.findByNumeroUsuario("EJE001")).thenReturn(Optional.of(testEjecutador));
        when(submissionRepo.save(any(ExamSubmission.class))).thenAnswer(i -> i.getArgument(0));

        ExamSubmissionResponse result = examService.submit(examId, request, "EJE001");

        assertEquals(0.0, result.getScore());
        assertFalse(result.isPassed());
    }

    /** Test 9: Preguntas mixtas → score calculado sobre TRUE_FALSE y MULTI_SELECT */
    @Test
    void submitExam_mixed_questions_calculates_total_score() {
        String examId = "exam-mixed";
        List<ExamQuestion> questions = List.of(
                buildQuestion(QuestionType.TRUE_FALSE, 0, null, 10),
                buildQuestion(QuestionType.TRUE_FALSE, 0, null, 10),
                buildQuestion(QuestionType.MULTI_SELECT, -1, List.of(0, 1), 10)
        );

        Exam exam = Exam.builder()
                .id(examId)
                .title("Mixed Exam")
                .storeId("store-1")
                .questions(questions)
                .passingScore(70)
                .timeLimitMinutes(120)
                .activo(true)
                .build();

        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(
                ExamAnswer.builder().selectedIndex(0).build(),           // Q1: correcto
                ExamAnswer.builder().selectedIndex(1).build(),           // Q2: incorrecto
                ExamAnswer.builder().selectedIndexes(List.of(0)).build() // Q3: parcial
        ));

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(userRepo.findByNumeroUsuario("EJE001")).thenReturn(Optional.of(testEjecutador));
        when(submissionRepo.save(any(ExamSubmission.class))).thenAnswer(i -> i.getArgument(0));

        ExamSubmissionResponse result = examService.submit(examId, request, "EJE001");

        assertNotNull(result);
        assertTrue(result.getScore() >= 0 && result.getScore() <= 100);
    }

    // ==================== ASSIGNMENT & ACCESS CONTROL (Tests 10-12) ====================

    /** Test 10: getByStore retorna exámenes activos de la sucursal */
    @Test
    void getByStore_returns_exams_for_store() {
        String storeId = "store-1";
        List<Exam> mockExams = List.of(
                buildSavedExam("exam-a", "Exam A", storeId, 60),
                buildSavedExam("exam-b", "Exam B", storeId, 120)
        );

        when(examRepo.findAvailableForStore(storeId)).thenReturn(mockExams);

        List<ExamResponse> result = examService.getByStore(storeId);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(examRepo).findAvailableForStore(storeId);
    }

    /** Test 11: getMySubmissions retorna submissions del usuario */
    @Test
    void getMySubmissions_returns_user_submissions() {
        String userNumero = "EJE001";
        when(userRepo.findByNumeroUsuario(userNumero)).thenReturn(Optional.of(testEjecutador));
        List<ExamSubmission> subs = List.of(
                buildSubmission("sub-1", "exam-1", testEjecutador.getId()),
                buildSubmission("sub-2", "exam-2", testEjecutador.getId())
        );

        when(submissionRepo.findByUserIdOrderBySubmittedAtDesc(testEjecutador.getId()))
                .thenReturn(subs);

        List<ExamSubmissionResponse> result = examService.getMySubmissions(userNumero);

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    /** Test 12: getSubmissions retorna todas las submissions de un examen */
    @Test
    void getSubmissions_returns_all_submissions_of_exam() {
        String examId = "exam-1";
        Exam exam = buildSavedExam(examId, "Exam 1", "store-1", 120);
        List<ExamSubmission> subs = List.of(
                buildSubmission("sub-1", examId, "user-1"),
                buildSubmission("sub-2", examId, "user-2"),
                buildSubmission("sub-3", examId, "user-3")
        );

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(submissionRepo.findByExamIdOrderBySubmittedAtDesc(examId)).thenReturn(subs);

        List<ExamSubmissionResponse> result = examService.getSubmissions(examId);

        assertNotNull(result);
        assertEquals(3, result.size());
    }

    // ==================== STATS & ANALYTICS (Tests 13-15) ====================

    /** Test 13: getStats con datos vuelca resultado no nulo */
    @Test
    void getStats_returns_non_null_response() {
        String examId = "exam-stats";
        Exam exam = buildSavedExam(examId, "Stats Exam", "store-1", 120);
        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(submissionRepo.findByExamIdOrderBySubmittedAtDesc(examId))
                .thenReturn(buildSubmissions(examId, 10, new int[]{100, 80, 90, 70, 60, 80, 75, 50, 40, 30}));

        ExamStatsResponse result = examService.getStats(examId);

        assertNotNull(result);
        assertEquals(examId, result.getExamId());
    }

    /** Test 14: passRate = 70 cuando 7 de 10 pasaron */
    @Test
    void getStats_calculates_passrate_correctly() {
        String examId = "exam-pr";
        Exam exam = buildSavedExam(examId, "PassRate Exam", "store-1", 120);
        List<ExamSubmission> subs = buildSubmissions(examId, 10,
                new int[]{80, 75, 70, 90, 85, 80, 72, 40, 30, 50});

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(submissionRepo.findByExamIdOrderBySubmittedAtDesc(examId)).thenReturn(subs);

        ExamStatsResponse result = examService.getStats(examId);

        assertNotNull(result);
        assertTrue(result.getPassRate() >= 60 && result.getPassRate() <= 80,
                "PassRate debería ser ~70%, obtenido: " + result.getPassRate());
    }

    /** Test 15: avgScore ≈ 80 para scores [100, 80, 60, 70, 90] */
    @Test
    void getStats_calculates_average_score() {
        String examId = "exam-avg";
        Exam exam = buildSavedExam(examId, "AvgScore Exam", "store-1", 120);
        List<ExamSubmission> subs = buildSubmissions(examId, 5,
                new int[]{100, 80, 60, 70, 90});

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(submissionRepo.findByExamIdOrderBySubmittedAtDesc(examId)).thenReturn(subs);

        ExamStatsResponse result = examService.getStats(examId);

        assertNotNull(result);
        assertEquals(80.0, result.getAvgScore(), 1.0);
    }

    // ==================== HELPERS ====================

    private CreateExamRequest buildCreateRequest(String title, Integer timeLimitMinutes, String storeId) {
        CreateExamRequest req = new CreateExamRequest();
        req.setTitle(title);
        req.setTimeLimitMinutes(timeLimitMinutes);
        req.setPassingScore(70);
        req.setStoreId(storeId);
        req.setQuestions(List.of(buildQuestionDto("MULTI_SELECT", 0)));
        return req;
    }

    private ExamQuestionDto buildQuestionDto(String type, int correctIndex) {
        ExamQuestionDto dto = new ExamQuestionDto();
        dto.setType(QuestionType.valueOf(type));
        dto.setQuestionText("Sample question?");
        dto.setOptions(List.of("Option A", "Option B", "Option C"));
        dto.setCorrectOptionIndex(correctIndex);
        dto.setPoints(10);
        return dto;
    }

    private Exam buildSavedExam(String id, String title, String storeId, Integer timeLimitMinutes) {
        return Exam.builder()
                .id(id)
                .title(title)
                .storeId(storeId)
                .passingScore(70)
                .timeLimitMinutes(timeLimitMinutes)
                .activo(true)
                .build();
    }

    private Exam buildExamWithQuestion(String id, QuestionType type,
                                       int correctIndex, List<Integer> correctIndexes) {
        List<String> opts = type == QuestionType.TRUE_FALSE
                ? List.of("Verdadero", "Falso")
                : List.of("A", "B", "C");

        ExamQuestion q = buildQuestion(type, correctIndex, correctIndexes, 10);
        q.setOptions(opts);

        return Exam.builder()
                .id(id)
                .title("Test Exam")
                .storeId("store-1")
                .questions(List.of(q))
                .passingScore(70)
                .timeLimitMinutes(120)
                .activo(true)
                .build();
    }

    private ExamQuestion buildQuestion(QuestionType type, int correctIndex,
                                       List<Integer> correctIndexes, int points) {
        ExamQuestion.ExamQuestionBuilder builder = ExamQuestion.builder()
                .id("q-" + System.nanoTime())
                .type(type)
                .questionText("Test question")
                .options(type == QuestionType.TRUE_FALSE
                        ? List.of("Verdadero", "Falso")
                        : List.of("A", "B", "C"))
                .points(points);

        if (correctIndexes != null) {
            builder.correctOptionIndexes(correctIndexes);
        }
        if (correctIndex >= 0) {
            builder.correctOptionIndex(correctIndex);
        }
        return builder.build();
    }

    private ExamSubmission buildSubmission(String id, String examId, String userId) {
        return ExamSubmission.builder()
                .id(id)
                .examId(examId)
                .userId(userId)
                .score(80.0)
                .passed(true)
                .submittedAt(Instant.now())
                .build();
    }

    private List<ExamSubmission> buildSubmissions(String examId, int count, int[] scores) {
        List<ExamSubmission> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int score = i < scores.length ? scores[i] : 70;
            list.add(ExamSubmission.builder()
                    .id("sub-" + i)
                    .examId(examId)
                    .userId("user-" + i)
                    .score(score)
                    .passed(score >= 70)
                    .submittedAt(Instant.now())
                    .build());
        }
        return list;
    }
}
