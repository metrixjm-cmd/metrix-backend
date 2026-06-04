package com.metrix.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrix.api.dto.*;
import com.metrix.api.model.QuestionType;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.security.JwtService;
import com.metrix.api.security.UserDetailsServiceImpl;
import com.metrix.api.service.ExamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FASE 1: ExamControllerWebMvcTest (20 tests)
 * Pruebas unitarias del controlador ExamController con MockMvc.
 */
@WebMvcTest(controllers = ExamController.class)
@Import(ExamControllerWebMvcTest.TestSecurityConfig.class)
class ExamControllerWebMvcTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        MongoMappingContext mongoMappingContext() {
            return new MongoMappingContext();
        }

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) ->
                            res.sendError(401)))
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable);
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExamService examService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    // ==================== AUTH & AUTHORIZATION (Tests 1-4) ====================

    /** Test 1: Usuario no autenticado recibe 401 */
    @Test
    void unauthenticated_user_cannot_access_exams() throws Exception {
        mockMvc.perform(get("/api/v1/exams/store/store-1"))
                .andExpect(status().isUnauthorized());
    }

    /** Test 2: ADMIN puede listar exámenes de una sucursal */
    @Test
    void admin_can_list_all_exams() throws Exception {
        when(examService.getByStore("store-1")).thenReturn(List.of(sampleExam("exam-1")));

        mockMvc.perform(get("/api/v1/exams/store/store-1")
                        .with(user("ADMIN001").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(examService).getByStore("store-1");
    }

    /** Test 3: GERENTE puede listar exámenes de su sucursal */
    @Test
    void gerente_can_list_exams() throws Exception {
        when(examService.getByStore("store-1")).thenReturn(List.of(sampleExam("exam-2")));

        mockMvc.perform(get("/api/v1/exams/store/store-1")
                        .with(user("GER001").roles("GERENTE")))
                .andExpect(status().isOk());

        verify(examService).getByStore("store-1");
    }

    /** Test 4: EJECUTADOR no puede listar exámenes por sucursal → 403 */
    @Test
    void ejecutador_cannot_list_exams_by_store() throws Exception {
        mockMvc.perform(get("/api/v1/exams/store/store-1")
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isForbidden());
    }

    // ==================== CREATE EXAM (Tests 5-10) ====================

    /** Test 5: ADMIN puede crear examen → 201 Created */
    @Test
    void admin_can_create_exam() throws Exception {
        CreateExamRequest request = new CreateExamRequest();
        request.setTitle("Math 101");
        request.setTimeLimitMinutes(120);
        request.setPassingScore(70);
        request.setStoreId("store-1");
        request.setQuestions(List.of(sampleQuestionDto()));

        when(examService.create(any(CreateExamRequest.class), anyString()))
                .thenReturn(sampleExam("exam-created"));

        mockMvc.perform(post("/api/v1/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("ADMIN001").roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("exam-created"));

        verify(examService).create(any(CreateExamRequest.class), eq("ADMIN001"));
    }

    /** Test 6: GERENTE NO puede crear examen → 403 Forbidden (solo ADMIN) */
    @Test
    void gerente_cannot_create_exam() throws Exception {
        CreateExamRequest request = new CreateExamRequest();
        request.setTitle("Store Operations");
        request.setTimeLimitMinutes(60);
        request.setPassingScore(70);
        request.setStoreId("store-2");
        request.setQuestions(List.of(sampleQuestionDto()));

        mockMvc.perform(post("/api/v1/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("GER001").roles("GERENTE")))
                .andExpect(status().isForbidden());
    }

    /** Test 7: EJECUTADOR no puede crear examen → 403 */
    @Test
    void ejecutador_cannot_create_exam() throws Exception {
        CreateExamRequest request = new CreateExamRequest();
        request.setTitle("Unauthorized");
        request.setStoreId("store-1");
        request.setQuestions(List.of(sampleQuestionDto()));

        mockMvc.perform(post("/api/v1/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isForbidden());
    }

    /** Test 8: Examen con duración válida de 1 hora (60 minutos) → 201 */
    @Test
    void exam_with_valid_duration_1_hour() throws Exception {
        CreateExamRequest request = new CreateExamRequest();
        request.setTitle("1-Hour Exam");
        request.setTimeLimitMinutes(60);
        request.setPassingScore(70);
        request.setStoreId("store-1");
        request.setQuestions(List.of(sampleQuestionDto()));

        ExamResponse response = sampleExam("exam-1h");
        response.setTimeLimitMinutes(60);

        when(examService.create(any(CreateExamRequest.class), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/v1/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("ADMIN001").roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeLimitMinutes").value(60));
    }

    /** Test 9: Examen con duración válida de 24 horas (1440 minutos) → 201 */
    @Test
    void exam_with_valid_duration_24_hours() throws Exception {
        CreateExamRequest request = new CreateExamRequest();
        request.setTitle("24-Hour Exam");
        request.setTimeLimitMinutes(1440);
        request.setPassingScore(70);
        request.setStoreId("store-1");
        request.setQuestions(List.of(sampleQuestionDto()));

        ExamResponse response = sampleExam("exam-24h");
        response.setTimeLimitMinutes(1440);

        when(examService.create(any(CreateExamRequest.class), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/v1/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("ADMIN001").roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeLimitMinutes").value(1440));
    }

    /** Test 10: Examen con duración de 5 horas (300 minutos) → 201 */
    @Test
    void exam_with_valid_duration_5_hours() throws Exception {
        CreateExamRequest request = new CreateExamRequest();
        request.setTitle("5-Hour Exam");
        request.setTimeLimitMinutes(300);
        request.setPassingScore(70);
        request.setStoreId("store-1");
        request.setQuestions(List.of(sampleQuestionDto()));

        ExamResponse response = sampleExam("exam-5h");
        response.setTimeLimitMinutes(300);

        when(examService.create(any(CreateExamRequest.class), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/v1/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("ADMIN001").roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeLimitMinutes").value(300));
    }

    // ==================== SUBMIT EXAM (Tests 11-17) ====================

    /** Test 11: EJECUTADOR puede responder examen → 201 Created */
    @Test
    void ejecutador_can_submit_exam_with_multiple_choice() throws Exception {
        String examId = "exam-1";
        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(ExamAnswer.builder().selectedIndex(0).build()));
        request.setTimeTakenSeconds(1800);

        ExamSubmissionResponse response = sampleSubmission(examId);

        when(examService.submit(eq(examId), any(SubmitExamRequest.class), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/exams/{examId}/submit", examId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").exists())
                .andExpect(jsonPath("$.passed").exists());

        verify(examService).submit(eq(examId), any(SubmitExamRequest.class), eq("EJE001"));
    }

    /** Test 12: TRUE_FALSE respuesta correcta → passed = true */
    @Test
    void true_false_correct_answer_scores_100() throws Exception {
        String examId = "exam-tf-correct";
        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(ExamAnswer.builder().selectedIndex(0).build()));

        ExamSubmissionResponse response = ExamSubmissionResponse.builder()
                .id("sub-tf")
                .examId(examId)
                .score(100.0)
                .passed(true)
                .submittedAt(Instant.now())
                .build();

        when(examService.submit(eq(examId), any(SubmitExamRequest.class), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/exams/{examId}/submit", examId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passed").value(true));
    }

    /** Test 13: TRUE_FALSE respuesta incorrecta → passed = false */
    @Test
    void true_false_incorrect_answer_scores_0() throws Exception {
        String examId = "exam-tf-wrong";
        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(ExamAnswer.builder().selectedIndex(1).build()));

        ExamSubmissionResponse response = ExamSubmissionResponse.builder()
                .id("sub-tf-w")
                .examId(examId)
                .score(0.0)
                .passed(false)
                .submittedAt(Instant.now())
                .build();

        when(examService.submit(eq(examId), any(SubmitExamRequest.class), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/exams/{examId}/submit", examId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passed").value(false));
    }

    /** Test 14: MULTI_SELECT 2 de 3 correctas → score ≈ 66 */
    @Test
    void multi_select_partial_answers_score_proportionally() throws Exception {
        String examId = "exam-ms";
        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(
                ExamAnswer.builder().selectedIndexes(List.of(0, 1)).build()
        ));

        ExamSubmissionResponse response = ExamSubmissionResponse.builder()
                .id("sub-ms")
                .examId(examId)
                .score(66.0)
                .passed(false)
                .submittedAt(Instant.now())
                .build();

        when(examService.submit(eq(examId), any(SubmitExamRequest.class), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/exams/{examId}/submit", examId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(66.0));
    }

    /** Test 15: OPEN_TEXT → hasPendingReview = true */
    @Test
    void open_text_answer_marked_pending_review() throws Exception {
        String examId = "exam-open";
        SubmitExamRequest request = new SubmitExamRequest();
        request.setAnswers(List.of(
                ExamAnswer.builder().textAnswer("Some detailed answer").build()
        ));

        ExamSubmissionResponse response = ExamSubmissionResponse.builder()
                .id("sub-ot")
                .examId(examId)
                .score(0.0)
                .passed(false)
                .hasPendingReview(true)
                .submittedAt(Instant.now())
                .build();

        when(examService.submit(eq(examId), any(SubmitExamRequest.class), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/exams/{examId}/submit", examId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasPendingReview").value(true));
    }

    /** Test 16: GET /{examId}/take → examen sin respuestas correctas */
    @Test
    void get_exam_for_take_without_correct_answers() throws Exception {
        String examId = "exam-for-take";
        ExamForTakeResponse response = ExamForTakeResponse.builder()
                .id(examId)
                .title("Quiz de Prueba")
                .timeLimitMinutes(60)
                .questionCount(3)
                .build();

        when(examService.getForTake(examId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/exams/{examId}/take", examId)
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(examId))
                .andExpect(jsonPath("$.timeLimitMinutes").value(60));
    }

    /** Test 17: GET /{examId}/attempt-info → info de intentos */
    @Test
    void get_attempt_info() throws Exception {
        String examId = "exam-attempts";
        AttemptInfoResponse response = AttemptInfoResponse.builder()
                .attemptCount(1)
                .maxAttempts(3)
                .canAttempt(true)
                .remainingAttempts(2)
                .build();

        when(examService.getAttemptInfo(examId, "EJE001")).thenReturn(response);

        mockMvc.perform(get("/api/v1/exams/{examId}/attempt-info", examId)
                        .with(user("EJE001").roles("EJECUTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptCount").value(1))
                .andExpect(jsonPath("$.canAttempt").value(true));
    }

    // ==================== STATS & ANALYTICS (Tests 18-20) ====================

    /** Test 18: ADMIN puede ver estadísticas del examen */
    @Test
    void admin_can_view_exam_statistics() throws Exception {
        String examId = "exam-stats";
        ExamStatsResponse stats = ExamStatsResponse.builder()
                .examId(examId)
                .examTitle("Stats Exam")
                .totalSubmissions(10L)
                .passedCount(7L)
                .passRate(75)
                .avgScore(78.5)
                .build();

        when(examService.getStats(examId)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/exams/{examId}/stats", examId)
                        .with(user("ADMIN001").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passRate").value(75))
                .andExpect(jsonPath("$.avgScore").value(78.5));

        verify(examService).getStats(examId);
    }

    /** Test 19: GERENTE puede ver detalle con respuestas correctas */
    @Test
    void get_exam_by_id_with_answers() throws Exception {
        String examId = "exam-detail";
        ExamResponse response = ExamResponse.builder()
                .id(examId)
                .title("Exam Detail")
                .storeId("store-1")
                .timeLimitMinutes(120)
                .questions(List.of(
                        ExamResponse.QuestionDto.builder()
                                .id("q1")
                                .type(QuestionType.TRUE_FALSE)
                                .questionText("Is 2+2=4?")
                                .options(List.of("Verdadero", "Falso"))
                                .correctOptionIndex(0)
                                .points(10)
                                .build()
                ))
                .build();

        when(examService.getById(examId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/exams/{examId}", examId)
                        .with(user("GER001").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(examId));

        verify(examService).getById(examId);
    }

    /** Test 20: Crear examen desde plantilla → 201 Created */
    @Test
    void create_exam_from_template() throws Exception {
        String templateId = "template-1";
        CreateExamFromTemplateRequest request = new CreateExamFromTemplateRequest();
        request.setStoreId("store-1");

        ExamResponse response = ExamResponse.builder()
                .id("exam-from-template")
                .title("Exam from Template")
                .storeId("store-1")
                .build();

        when(examService.createFromTemplate(eq(templateId), any(CreateExamFromTemplateRequest.class), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/exams/from-template/{templateId}", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("ADMIN001").roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("exam-from-template"));

        verify(examService).createFromTemplate(eq(templateId), any(CreateExamFromTemplateRequest.class), eq("ADMIN001"));
    }

    /** Test 21 (Regression): GERENTE NO puede crear examen desde plantilla → 403 Forbidden
     * Regression: solo ADMIN crea exámenes — regla de negocio 2026-06-04
     */
    @Test
    void gerente_cannot_create_exam_from_template() throws Exception {
        CreateExamFromTemplateRequest request = new CreateExamFromTemplateRequest();
        request.setStoreId("store-1");

        mockMvc.perform(post("/api/v1/exams/from-template/{templateId}", "template-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user("GER001").roles("GERENTE")))
                .andExpect(status().isForbidden());
    }

    // ==================== HELPERS ====================

    private ExamResponse sampleExam(String id) {
        return ExamResponse.builder()
                .id(id)
                .title("Sample Exam " + id)
                .storeId("store-1")
                .timeLimitMinutes(120)
                .passingScore(70)
                .createdByName("Admin User")
                .createdAt(Instant.now())
                .build();
    }

    private ExamQuestionDto sampleQuestionDto() {
        ExamQuestionDto dto = new ExamQuestionDto();
        dto.setType(QuestionType.MULTIPLE_CHOICE);
        dto.setQuestionText("What is the capital of France?");
        dto.setOptions(List.of("Paris", "London", "Berlin", "Madrid"));
        dto.setCorrectOptionIndex(0);
        dto.setPoints(10);
        return dto;
    }

    private ExamSubmissionResponse sampleSubmission(String examId) {
        return ExamSubmissionResponse.builder()
                .id("sub-1")
                .examId(examId)
                .score(100.0)
                .passed(true)
                .submittedAt(Instant.now())
                .build();
    }
}
