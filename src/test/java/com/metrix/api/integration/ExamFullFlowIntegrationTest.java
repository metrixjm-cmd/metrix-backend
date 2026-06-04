package com.metrix.api.integration;

import com.metrix.api.dto.*;
import com.metrix.api.model.*;
import com.metrix.api.repository.ExamRepository;
import java.util.Set;
import com.metrix.api.repository.ExamSubmissionRepository;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.service.ExamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FASE 3: ExamFullFlowIntegrationTest (5 scenarios)
 * Pruebas de integración con contexto completo de Spring y MongoDB real.
 * Requiere MongoDB corriendo en localhost:27017/metrix_db (perfil test).
 */
@SpringBootTest
@ActiveProfiles("test")
class ExamFullFlowIntegrationTest {

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamSubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExamService examService;

    private User adminUser;
    private User gerenteUser;
    private User ejecutadorUser;

    @BeforeEach
    void setup() {
        // Limpiar datos de prueba (solo los que creamos)
        submissionRepository.deleteAll();
        examRepository.deleteAll();

        // Buscar o crear usuarios de prueba
        adminUser = userRepository.findByNumeroUsuario("ADMIN001")
                .orElseGet(() -> userRepository.save(User.builder()
                        .numeroUsuario("ADMIN001")
                        .nombre("Admin Test")
                        .roles(Set.of(Role.ADMIN))
                        .storeId("store-test-1")
                        .build()));

        gerenteUser = userRepository.findByNumeroUsuario("GER001")
                .orElseGet(() -> userRepository.save(User.builder()
                        .numeroUsuario("GER001")
                        .nombre("Gerente Test")
                        .roles(Set.of(Role.GERENTE))
                        .storeId("store-test-1")
                        .build()));

        ejecutadorUser = userRepository.findByNumeroUsuario("EJE001")
                .orElseGet(() -> userRepository.save(User.builder()
                        .numeroUsuario("EJE001")
                        .nombre("Ejecutador Test")
                        .roles(Set.of(Role.EJECUTADOR))
                        .storeId("store-test-1")
                        .build()));
    }

    // ==================== SCENARIO 1 ====================

    /**
     * Escenario 1: ADMIN crea examen, se persiste en MongoDB.
     * Duración, preguntas y sucursal se almacenan correctamente.
     */
    @Test
    void scenario_1_admin_creates_exam_persisted_correctly() {
        CreateExamRequest req = new CreateExamRequest();
        req.setTitle("Leadership 101");
        req.setTimeLimitMinutes(120);
        req.setPassingScore(70);
        req.setStoreId(adminUser.getStoreId());
        req.setQuestions(List.of(
                buildQuestionDto(QuestionType.TRUE_FALSE,
                        "Buenos managers delegan", List.of("Verdadero", "Falso"), 0, null, 10)
        ));

        ExamResponse exam = examService.create(req, adminUser.getNumeroUsuario());

        assertNotNull(exam);
        assertNotNull(exam.getId());
        assertEquals("Leadership 101", exam.getTitle());
        assertEquals(120, exam.getTimeLimitMinutes());

        // Verify en BD
        Exam saved = examRepository.findById(exam.getId()).orElseThrow();
        assertEquals("Leadership 101", saved.getTitle());
        assertEquals(adminUser.getStoreId(), saved.getStoreId());
    }

    // ==================== SCENARIO 2 ====================

    /**
     * Escenario 2: GERENTE crea examen multi-pregunta, EJECUTADOR responde y se califica.
     */
    @Test
    void scenario_2_gerente_creates_and_ejecutador_completes_exam() {
        // GERENTE crea examen con 3 preguntas
        CreateExamRequest req = new CreateExamRequest();
        req.setTitle("Store Operations Quiz");
        req.setTimeLimitMinutes(60);
        req.setPassingScore(70);
        req.setStoreId(gerenteUser.getStoreId());
        req.setQuestions(List.of(
                buildQuestionDto(QuestionType.TRUE_FALSE,
                        "¿Qué significa SKU?",
                        List.of("Verdadero", "Falso"),
                        0, null, 10),
                buildQuestionDto(QuestionType.TRUE_FALSE,
                        "El inventario es siempre exacto",
                        List.of("Verdadero", "Falso"),
                        1, null, 10),
                buildQuestionDto(QuestionType.MULTI_SELECT,
                        "¿Qué es crítico?",
                        List.of("Atender clientes", "Ordenar stock", "Redes sociales"),
                        -1, List.of(0, 1), 10)
        ));

        ExamResponse exam = examService.create(req, gerenteUser.getNumeroUsuario());
        assertNotNull(exam);
        assertEquals(3, exam.getQuestions().size());

        // EJECUTADOR responde
        SubmitExamRequest submitReq = new SubmitExamRequest();
        submitReq.setAnswers(List.of(
                ExamAnswer.builder().selectedIndex(0).build(),              // Q1: correcto
                ExamAnswer.builder().selectedIndex(1).build(),              // Q2: correcto
                ExamAnswer.builder().selectedIndexes(List.of(0, 1)).build() // Q3: correcto
        ));
        submitReq.setTimeTakenSeconds(1800);

        ExamSubmissionResponse submission = examService.submit(
                exam.getId(), submitReq, ejecutadorUser.getNumeroUsuario());

        assertNotNull(submission);
        assertNotNull(submission.getId());
        assertTrue(submission.isPassed());
        assertTrue(submission.getScore() >= 70.0);

        // Verify submission en BD
        ExamSubmission saved = submissionRepository.findById(submission.getId()).orElseThrow();
        assertEquals(exam.getId(), saved.getExamId());
        assertEquals(ejecutadorUser.getId(), saved.getUserId());
    }

    // ==================== SCENARIO 3 ====================

    /**
     * Escenario 3: Validar persistencia de duración 1h y 24h.
     */
    @Test
    void scenario_3_exam_timer_1h_and_24h_persist_correctly() {
        // 1 hora
        CreateExamRequest req1h = new CreateExamRequest();
        req1h.setTitle("1-Hour Exam");
        req1h.setTimeLimitMinutes(60);
        req1h.setPassingScore(70);
        req1h.setStoreId(adminUser.getStoreId());
        req1h.setQuestions(List.of(buildSimpleQuestion()));

        ExamResponse exam1h = examService.create(req1h, adminUser.getNumeroUsuario());
        assertEquals(60, exam1h.getTimeLimitMinutes());
        assertTrue(examRepository.findById(exam1h.getId()).isPresent());

        // 24 horas
        CreateExamRequest req24h = new CreateExamRequest();
        req24h.setTitle("24-Hour Exam");
        req24h.setTimeLimitMinutes(1440);
        req24h.setPassingScore(70);
        req24h.setStoreId(adminUser.getStoreId());
        req24h.setQuestions(List.of(buildSimpleQuestion()));

        ExamResponse exam24h = examService.create(req24h, adminUser.getNumeroUsuario());
        assertEquals(1440, exam24h.getTimeLimitMinutes());
        assertTrue(examRepository.findById(exam24h.getId()).isPresent());
    }

    // ==================== SCENARIO 4 ====================

    /**
     * Escenario 4: Los 4 tipos de pregunta se preservan en MongoDB.
     */
    @Test
    void scenario_4_all_question_types_preserved_in_mongodb() {
        CreateExamRequest req = new CreateExamRequest();
        req.setTitle("Type Preservation Test");
        req.setTimeLimitMinutes(120);
        req.setPassingScore(70);
        req.setStoreId(adminUser.getStoreId());
        req.setQuestions(List.of(
                buildQuestionDto(QuestionType.TRUE_FALSE,
                        "Q1 - True/False", List.of("V", "F"), 0, null, 10),
                buildQuestionDto(QuestionType.TRUE_FALSE,
                        "Q2 - True/False 2", List.of("V", "F"), 1, null, 10),
                buildQuestionDto(QuestionType.MULTI_SELECT,
                        "Q3 - Multi Select", List.of("1", "2", "3"), -1, List.of(0, 2), 10)
        ));

        ExamResponse exam = examService.create(req, adminUser.getNumeroUsuario());

        assertNotNull(exam.getQuestions());
        assertEquals(3, exam.getQuestions().size());

        // Verify tipos en BD
        Exam saved = examRepository.findById(exam.getId()).orElseThrow();
        List<ExamQuestion> questions = saved.getQuestions();
        assertEquals(QuestionType.TRUE_FALSE,   questions.get(0).getType());
        assertEquals(QuestionType.TRUE_FALSE,   questions.get(1).getType());
        assertEquals(QuestionType.MULTI_SELECT, questions.get(2).getType());
    }

    // ==================== SCENARIO 5 ====================

    /**
     * Escenario 5: Examen vinculado a capacitación (trainingId).
     */
    @Test
    void scenario_5_exam_linked_to_training_persists_trainingId() {
        String trainingId = "training-multi-day-001";

        CreateExamRequest req = new CreateExamRequest();
        req.setTitle("Customer Service Exam");
        req.setTimeLimitMinutes(120);
        req.setPassingScore(70);
        req.setStoreId(gerenteUser.getStoreId());
        req.setTrainingId(trainingId);
        req.setQuestions(List.of(
                buildSimpleQuestion(),
                buildQuestionDto(QuestionType.TRUE_FALSE,
                        "¿Hay que saludar al cliente?",
                        List.of("Verdadero", "Falso"),
                        0, null, 10)
        ));

        ExamResponse exam = examService.create(req, gerenteUser.getNumeroUsuario());

        assertNotNull(exam);
        assertEquals(trainingId, exam.getTrainingId());
        assertEquals(2, exam.getQuestions().size());

        // Verify en BD
        Exam saved = examRepository.findById(exam.getId()).orElseThrow();
        assertEquals(trainingId, saved.getTrainingId());
    }

    // ==================== HELPERS ====================

    private ExamQuestionDto buildQuestionDto(QuestionType type, String text,
                                              List<String> options, int correctIndex,
                                              List<Integer> correctIndexes, int points) {
        ExamQuestionDto dto = new ExamQuestionDto();
        dto.setType(type);
        dto.setQuestionText(text);
        dto.setOptions(options);
        if (correctIndex >= 0) {
            dto.setCorrectOptionIndex(correctIndex);
        }
        if (correctIndexes != null) {
            dto.setCorrectOptionIndexes(correctIndexes);
        }
        dto.setPoints(points);
        return dto;
    }

    private ExamQuestionDto buildSimpleQuestion() {
        return buildQuestionDto(QuestionType.TRUE_FALSE,
                "Simple question",
                List.of("Verdadero", "Falso"),
                0, null, 10);
    }
}
