package com.metrix.api.service;

import com.metrix.api.dto.CorrectionSpeedResponse;
import com.metrix.api.dto.ExamKpiResponse;
import com.metrix.api.dto.IgeoAnalyticsResponse;
import com.metrix.api.dto.IncidentKpiResponse;
import com.metrix.api.dto.KpiSummaryResponse;
import com.metrix.api.dto.LabelCount;
import com.metrix.api.dto.ShiftBreakdownResponse;
import com.metrix.api.dto.StoreRankingResponse;
import com.metrix.api.dto.TrainingKpiResponse;
import com.metrix.api.dto.UserResponsibilityResponse;
import com.metrix.api.model.Exam;
import com.metrix.api.model.ExamSubmission;
import com.metrix.api.model.Incident;
import com.metrix.api.model.IncidentCategory;
import com.metrix.api.model.IncidentSeverity;
import com.metrix.api.model.IncidentStatus;
import com.metrix.api.model.StatusTransition;
import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.model.Training;
import com.metrix.api.model.TrainingStatus;
import com.metrix.api.model.User;
import com.metrix.api.model.Store;
import com.metrix.api.repository.ExamRepository;
import com.metrix.api.repository.ExamSubmissionRepository;
import com.metrix.api.repository.IncidentRepository;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.repository.TrainingRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.metrix.api.dto.StatusCount;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Implementación de KPIs METRIX — Sprint 7.
 * <p>
 * KPIs implementados: #1 OnTimeRate, #2 DelegaciónEfectiva, #3 ReworkRate,
 * #4 AvgExecMin, #5 ShiftBreakdown, #8 CriticalPending, #10 IGEO.
 * <p>
 * Todas las fórmulas operan en memoria sobre la lista de tareas recuperada por
 * {@link TaskRepository}; no se ejecutan aggregations de MongoDB en este sprint.
 */
@Service
@RequiredArgsConstructor
public class KpiServiceImpl implements KpiService {

    private static final Logger log = LoggerFactory.getLogger(KpiServiceImpl.class);

    // ── Repositorios (inyección por constructor vía @RequiredArgsConstructor) ─
    private final TaskRepository             taskRepository;
    private final UserRepository             userRepository;
    private final TrainingRepository         trainingRepository;
    private final StoreRepository            storeRepository;
    private final IncidentRepository         incidentRepository;
    private final ExamRepository             examRepository;
    private final ExamSubmissionRepository   examSubmissionRepository;

    // ── Cliente HTTP para analytics-service Python (Sprint 17) ───────────────
    // RestTemplate se inyecta por constructor junto con los repositorios.
    private final RestTemplate restTemplate;

    // @Value usa inyección de campo (field injection). Funciona correctamente
    // en paralelo al constructor de Lombok — Spring aplica @Value después
    // de construir el bean.
    @Value("${metrix.analytics.url}")
    private String analyticsUrl;

    @Value("${metrix.analytics.api-key:dev-internal-key}")
    private String analyticsApiKey;

    // ── Puntos de entrada ─────────────────────────────────────────────────

    @Cacheable(value = "kpiSummary", key = "#storeId")
    @Override
    public KpiSummaryResponse getStoreSummary(String storeId) {
        List<Task> tasks = taskRepository.findByStoreIdAndActivoTrue(storeId);
        return buildSummary(tasks, "STORE", storeId);
    }

    @Override
    public KpiSummaryResponse getUserSummary(String userId) {
        List<Task> tasks = taskRepository.findByAssignedUserIdAndActivoTrue(userId);
        return buildSummary(tasks, "USER", userId);
    }

    @Cacheable(value = "storeRanking")
    @Override
    public List<StoreRankingResponse> getStoreRanking() {
        List<Task> all = taskRepository.findByActivoTrue();
        Map<String, List<Task>> byStore = all.stream()
                .collect(Collectors.groupingBy(Task::getStoreId));

        // Pre-cargar nombres de sucursales para evitar N+1
        Map<String, String> storeNames = storeRepository.findByActivoTrue().stream()
                .collect(Collectors.toMap(Store::getId, Store::getNombre, (a, b) -> a));

        List<StoreRankingResponse> ranking = byStore.entrySet().stream()
                .map(e -> {
                    List<Task> tasks  = e.getValue();
                    List<Task> closed = closedTasks(tasks);
                    double otr  = computeOnTimeRate(closed);
                    double rwr  = computeReworkRate(tasks);
                    double qsc  = computeQualityScore(tasks);
                    double igeo = computeIgeo(otr, rwr, qsc);
                    return StoreRankingResponse.builder()
                            .storeId(e.getKey())
                            .storeName(storeNames.getOrDefault(e.getKey(), e.getKey()))
                            .igeo(round2(igeo))
                            .onTimeRate(round2(otr))
                            .reworkRate(round2(rwr))
                            .totalTasks(tasks.size())
                            .completedTasks((int) countByStatus(tasks, TaskStatus.COMPLETED))
                            .failedTasks((int) countByStatus(tasks, TaskStatus.FAILED))
                            .build();
                })
                .sorted(Comparator.comparingDouble(StoreRankingResponse::getIgeo).reversed())
                .collect(Collectors.toList());

        // Asignar rank 1-based
        for (int i = 0; i < ranking.size(); i++) {
            ranking.get(i).setRank(i + 1);
        }
        return ranking;
    }

    // ── KPI #7 — Responsabilidad Individual ──────────────────────────────

    @Cacheable(value = "kpiSummary", key = "'users-' + #storeId")
    @Override
    public List<UserResponsibilityResponse> getUsersResponsibility(String storeId) {
        List<User> users = userRepository.findByStoreIdAndActivoTrue(storeId);

        // Batch fetch: 1 sola query para TODAS las tareas de la sucursal (elimina N+1)
        List<Task> allStoreTasks = taskRepository.findByStoreIdAndActivoTrue(storeId);
        Map<String, List<Task>> tasksByUser = allStoreTasks.stream()
                .filter(t -> t.getAssignedUserId() != null)
                .collect(Collectors.groupingBy(Task::getAssignedUserId));

        List<UserResponsibilityResponse> result = users.stream()
                .map(user -> {
                    List<Task> tasks     = tasksByUser.getOrDefault(user.getId(), List.of());
                    List<Task> closed    = closedTasks(tasks);
                    List<Task> completed = completedTasks(tasks);

                    double otr  = computeOnTimeRate(closed);
                    double rwr  = computeReworkRate(tasks);
                    double qsc  = computeQualityScore(tasks);
                    double igeo = computeIgeo(otr, rwr, qsc);

                    return UserResponsibilityResponse.builder()
                            .userId(user.getId())
                            .nombre(user.getNombre())
                            .position(user.getPuesto())
                            .turno(user.getTurno())
                            .totalTasks(tasks.size())
                            .completedTasks((int) countByStatus(tasks, TaskStatus.COMPLETED))
                            .failedTasks((int) countByStatus(tasks, TaskStatus.FAILED))
                            .onTimeRate(round2(otr))
                            .reworkRate(round2(rwr))
                            .avgExecMinutes(round2(computeAvgExecutionMinutes(completed)))
                            .igeo(round2(igeo))
                            .build();
                })
                .sorted(Comparator.comparingDouble(UserResponsibilityResponse::getIgeo).reversed())
                .collect(Collectors.toList());

        // Asignar rank 1-based
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setRank(i + 1);
        }
        return result;
    }

    // ── KPI #10 — IGEO analítico (analytics-service Python, Sprint 17) ──────

    /**
     * Delega el cálculo del IGEO completo (4 pilares) al microservicio Python.
     * <p>
     * Flujo:
     * <ol>
     *   <li>Llama {@code GET {analyticsUrl}/igeo} vía {@link RestTemplate}.</li>
     *   <li>Deserializa el JSON en {@link IgeoAnalyticsResponse} (records Java 21).</li>
     *   <li>Si el servicio no responde, lanza {@link RestClientException} con mensaje
     *       claro para que el controller devuelva HTTP 503.</li>
     * </ol>
     * <p>
     * No cache: cada llamada ejecuta el cálculo en tiempo real contra MongoDB.
     * Para producción considerar añadir {@code @Cacheable("igeo")} con TTL de 5 min.
     */
    @Override
    public IgeoAnalyticsResponse getGlobalIgeoAnalytics() {
        String url = analyticsUrl + "/igeo";
        log.debug("[KPI#10] Llamando analytics-service: GET {}", url);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", analyticsApiKey);
            ResponseEntity<IgeoAnalyticsResponse> entity = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), IgeoAnalyticsResponse.class);
            IgeoAnalyticsResponse response = entity.getBody();
            if (response == null) {
                throw new RestClientException("analytics-service devolvió cuerpo vacío en " + url);
            }
            log.debug("[KPI#10] IGEO global recibido: {}", response.data() != null
                    ? response.data().global().igeo() : "sin datos");
            return response;
        } catch (RestClientException ex) {
            log.error("[KPI#10] analytics-service no disponible en {}: {}", url, ex.getMessage());
            throw ex;
        }
    }

    // ── KPI #9 — Velocidad de Corrección ─────────────────────────────────

    @Override
    public CorrectionSpeedResponse getCorrectionSpeed(String storeId) {
        List<Task> tasks = taskRepository.findByStoreIdAndActivoTrue(storeId);

        // Solo tareas con rework que tienen historial de transiciones
        List<double[]> correctionTimes = tasks.stream()
                .filter(t -> t.getReworkCount() > 0
                        && t.getTransitions() != null
                        && !t.getTransitions().isEmpty())
                .flatMap(t -> extractCorrectionMinutes(t).stream())
                .collect(Collectors.toList());

        if (correctionTimes.isEmpty()) {
            return CorrectionSpeedResponse.builder()
                    .storeId(storeId)
                    .reworkedTasks(0)
                    .avgCorrectionMinutes(-1.0)
                    .minCorrectionMinutes(-1.0)
                    .maxCorrectionMinutes(-1.0)
                    .build();
        }

        double[] values = correctionTimes.stream().mapToDouble(d -> d[0]).toArray();
        double avg = Arrays.stream(values).average().orElse(-1.0);
        double min = Arrays.stream(values).min().orElse(-1.0);
        double max = Arrays.stream(values).max().orElse(-1.0);

        return CorrectionSpeedResponse.builder()
                .storeId(storeId)
                .reworkedTasks(correctionTimes.size())
                .avgCorrectionMinutes(round2(avg))
                .minCorrectionMinutes(round2(min))
                .maxCorrectionMinutes(round2(max))
                .build();
    }

    /**
     * Para una tarea con rework, extrae las duraciones en minutos de cada ciclo
     * FAILED → PENDING/IN_PROGRESS → COMPLETED encontrado en el historial.
     */
    private List<double[]> extractCorrectionMinutes(Task task) {
        List<StatusTransition> transitions = task.getTransitions();
        if (transitions == null || transitions.size() < 2) {
            return List.of();
        }

        List<double[]> durations = new ArrayList<>();

        for (int i = 0; i < transitions.size() - 1; i++) {
            StatusTransition t = transitions.get(i);
            if (t.getToStatus() == TaskStatus.FAILED && t.getChangedAt() != null) {
                for (int j = i + 1; j < transitions.size(); j++) {
                    StatusTransition next = transitions.get(j);
                    if (next.getToStatus() == TaskStatus.COMPLETED && next.getChangedAt() != null) {
                        long minutes = Duration.between(t.getChangedAt(),
                                next.getChangedAt()).toMinutes();
                        if (minutes >= 0) {
                            durations.add(new double[]{minutes});
                        }
                        break;
                    }
                }
            }
        }
        return durations;
    }

    // ── KPIs de Incidencias ───────────────────────────────────────────────

    @Cacheable(value = "kpiIncidents", key = "#storeId")
    @Override
    public IncidentKpiResponse getIncidentKpis(String storeId) {
        List<Incident> incidents = incidentRepository.findByStoreIdAndActivoTrue(storeId);
        long total = incidents.size();

        if (total == 0) {
            return IncidentKpiResponse.builder()
                    .storeId(storeId).total(0)
                    .abiertas(0).enResolucion(0).cerradas(0)
                    .resolutionRate(-1.0).criticalOpen(0).avgResolutionHours(-1.0)
                    .bySeverity(zeroLabelCounts(enumNames(IncidentSeverity.values())))
                    .byCategory(zeroLabelCounts(enumNames(IncidentCategory.values())))
                    .build();
        }

        long abiertas     = incidents.stream().filter(i -> i.getStatus() == IncidentStatus.ABIERTA).count();
        long enResolucion = incidents.stream().filter(i -> i.getStatus() == IncidentStatus.EN_RESOLUCION).count();
        long cerradas     = incidents.stream().filter(i -> i.getStatus() == IncidentStatus.CERRADA).count();

        long criticalOpen = incidents.stream()
                .filter(i -> i.getSeverity() == IncidentSeverity.CRITICA && i.getStatus() != IncidentStatus.CERRADA)
                .count();

        double avgResMinutes = incidents.stream()
                .filter(i -> i.getStatus() == IncidentStatus.CERRADA
                        && i.getResolvedAt() != null && i.getCreatedAt() != null)
                .mapToLong(i -> Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes())
                .average()
                .orElse(-1.0);

        Map<String, Long> sevCounts = incidents.stream()
                .filter(i -> i.getSeverity() != null)
                .collect(Collectors.groupingBy(i -> i.getSeverity().name(), Collectors.counting()));
        Map<String, Long> catCounts = incidents.stream()
                .filter(i -> i.getCategory() != null)
                .collect(Collectors.groupingBy(i -> i.getCategory().name(), Collectors.counting()));

        return IncidentKpiResponse.builder()
                .storeId(storeId).total(total)
                .abiertas(abiertas).enResolucion(enResolucion).cerradas(cerradas)
                .resolutionRate(round2(cerradas * 100.0 / total))
                .criticalOpen(criticalOpen)
                .avgResolutionHours(avgResMinutes < 0 ? -1.0 : round2(avgResMinutes / 60.0))
                .bySeverity(orderedLabelCounts(enumNames(IncidentSeverity.values()), sevCounts, total))
                .byCategory(orderedLabelCounts(enumNames(IncidentCategory.values()), catCounts, total))
                .build();
    }

    // ── KPIs de Capacitaciones ────────────────────────────────────────────

    @Cacheable(value = "kpiTrainings", key = "#storeId")
    @Override
    public TrainingKpiResponse getTrainingKpis(String storeId) {
        List<Training> trainings = trainingRepository.findByStoreIdAndActivoTrue(storeId);
        long total = trainings.size();

        if (total == 0) {
            return TrainingKpiResponse.builder()
                    .storeId(storeId).total(0)
                    .programadas(0).enCurso(0).completadas(0).noCompletadas(0)
                    .completionRate(-1.0).onTimeRate(-1.0).passRate(-1.0).avgGrade(-1.0).avgProgress(0.0)
                    .overduePending(0).byCategory(List.of())
                    .build();
        }

        long programadas   = countTrainingStatus(trainings, TrainingStatus.PROGRAMADA);
        long enCurso       = countTrainingStatus(trainings, TrainingStatus.EN_CURSO);
        long completadas   = countTrainingStatus(trainings, TrainingStatus.COMPLETADA);
        long noCompletadas = countTrainingStatus(trainings, TrainingStatus.NO_COMPLETADA);

        List<Training> completed = trainings.stream()
                .filter(t -> t.getProgress() != null && t.getProgress().getStatus() == TrainingStatus.COMPLETADA)
                .collect(Collectors.toList());
        double onTimeRate = completed.isEmpty() ? -1.0 : round2(completed.stream()
                .filter(t -> Boolean.TRUE.equals(t.getProgress().getOnTime())).count() * 100.0 / completed.size());

        List<Training> withVerdict = trainings.stream()
                .filter(t -> t.getProgress() != null && t.getProgress().getPassed() != null)
                .collect(Collectors.toList());
        double passRate = withVerdict.isEmpty() ? -1.0 : round2(withVerdict.stream()
                .filter(t -> Boolean.TRUE.equals(t.getProgress().getPassed())).count() * 100.0 / withVerdict.size());

        OptionalDouble avgGradeOpt = trainings.stream()
                .filter(t -> t.getProgress() != null && t.getProgress().getGrade() != null)
                .mapToDouble(t -> t.getProgress().getGrade())
                .average();

        double avgProgress = round2(trainings.stream()
                .filter(t -> t.getProgress() != null)
                .mapToInt(t -> t.getProgress().getPercentage())
                .average()
                .orElse(0.0));

        Instant now = Instant.now();
        long overduePending = trainings.stream()
                .filter(t -> t.getProgress() != null
                        && (t.getProgress().getStatus() == TrainingStatus.PROGRAMADA
                            || t.getProgress().getStatus() == TrainingStatus.EN_CURSO)
                        && t.getDueAt() != null && t.getDueAt().isBefore(now))
                .count();

        Map<String, Long> catCounts = trainings.stream()
                .map(t -> (t.getCategory() != null && !t.getCategory().isBlank()) ? t.getCategory() : "Sin categoría")
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
        List<LabelCount> byCategory = catCounts.entrySet().stream()
                .map(e -> LabelCount.builder()
                        .label(e.getKey())
                        .count(e.getValue())
                        .percentage((int) Math.round(e.getValue() * 100.0 / total))
                        .build())
                .sorted(Comparator.comparingLong(LabelCount::getCount).reversed())
                .collect(Collectors.toList());

        return TrainingKpiResponse.builder()
                .storeId(storeId).total(total)
                .programadas(programadas).enCurso(enCurso)
                .completadas(completadas).noCompletadas(noCompletadas)
                .completionRate(round2(completadas * 100.0 / total))
                .onTimeRate(onTimeRate)
                .passRate(passRate)
                .avgGrade(avgGradeOpt.isPresent() ? round2(avgGradeOpt.getAsDouble()) : -1.0)
                .avgProgress(avgProgress)
                .overduePending(overduePending)
                .byCategory(byCategory)
                .build();
    }

    private long countTrainingStatus(List<Training> list, TrainingStatus status) {
        return list.stream()
                .filter(t -> t.getProgress() != null && t.getProgress().getStatus() == status)
                .count();
    }

    // ── KPIs de Exámenes ──────────────────────────────────────────────────

    @Cacheable(value = "kpiExams", key = "#storeId")
    @Override
    public ExamKpiResponse getExamKpis(String storeId) {
        List<Exam> exams = examRepository.findByStoreIdAndActivoTrue(storeId);
        List<ExamSubmission> subs = examSubmissionRepository.findByStoreId(storeId);
        long totalExams = exams.size();
        long totalSubs  = subs.size();

        if (totalSubs == 0) {
            return ExamKpiResponse.builder()
                    .storeId(storeId).totalExams(totalExams).totalSubmissions(0)
                    .passRate(-1.0).avgScore(-1.0).minScore(0).maxScore(0).avgTimeSecs(-1.0)
                    .scoreDistribution(emptyScoreDistribution())
                    .perExam(buildExamRows(exams, subs))
                    .perUser(List.of())
                    .build();
        }

        long passed = subs.stream().filter(ExamSubmission::isPassed).count();

        List<Integer> times = subs.stream()
                .filter(s -> s.getTimeTakenSeconds() != null && s.getTimeTakenSeconds() > 0)
                .map(ExamSubmission::getTimeTakenSeconds)
                .collect(Collectors.toList());

        long r0  = subs.stream().filter(s -> s.getScore() < 50).count();
        long r50 = subs.stream().filter(s -> s.getScore() >= 50 && s.getScore() < 70).count();
        long r70 = subs.stream().filter(s -> s.getScore() >= 70 && s.getScore() < 90).count();
        long r90 = subs.stream().filter(s -> s.getScore() >= 90).count();

        return ExamKpiResponse.builder()
                .storeId(storeId).totalExams(totalExams).totalSubmissions(totalSubs)
                .passRate(round2(passed * 100.0 / totalSubs))
                .avgScore(round2(subs.stream().mapToDouble(ExamSubmission::getScore).average().orElse(0)))
                .minScore(subs.stream().mapToDouble(ExamSubmission::getScore).min().orElse(0))
                .maxScore(subs.stream().mapToDouble(ExamSubmission::getScore).max().orElse(0))
                .avgTimeSecs(times.isEmpty() ? -1.0 : round2(times.stream().mapToInt(i -> i).average().orElse(0)))
                .scoreDistribution(List.of(
                        labelCount("0–49",   r0,  totalSubs),
                        labelCount("50–69",  r50, totalSubs),
                        labelCount("70–89",  r70, totalSubs),
                        labelCount("90–100", r90, totalSubs)))
                .perExam(buildExamRows(exams, subs))
                .perUser(buildUserRows(subs))
                .build();
    }

    private List<ExamKpiResponse.ExamUserRow> buildUserRows(List<ExamSubmission> subs) {
        Map<String, List<ExamSubmission>> byUser = subs.stream()
                .filter(s -> s.getUserId() != null)
                .collect(Collectors.groupingBy(ExamSubmission::getUserId));

        return byUser.entrySet().stream()
                .map(e -> {
                    List<ExamSubmission> us = e.getValue();
                    long t = us.size();
                    long passed = us.stream().filter(ExamSubmission::isPassed).count();
                    return ExamKpiResponse.ExamUserRow.builder()
                            .userId(e.getKey())
                            .userName(us.get(0).getUserName())
                            .submissions(t)
                            .passed(passed)
                            .passRate((int) Math.round(passed * 100.0 / t))
                            .avgScore(round2(us.stream().mapToDouble(ExamSubmission::getScore).average().orElse(0)))
                            .build();
                })
                .sorted(Comparator.comparingDouble(ExamKpiResponse.ExamUserRow::getAvgScore).reversed())
                .collect(Collectors.toList());
    }

    private List<ExamKpiResponse.ExamRow> buildExamRows(List<Exam> exams, List<ExamSubmission> subs) {
        Map<String, List<ExamSubmission>> byExam = subs.stream()
                .filter(s -> s.getExamId() != null)
                .collect(Collectors.groupingBy(ExamSubmission::getExamId));

        return exams.stream()
                .map(e -> {
                    List<ExamSubmission> es = byExam.getOrDefault(e.getId(), List.of());
                    long t = es.size();
                    int passRate = t > 0
                            ? (int) Math.round(es.stream().filter(ExamSubmission::isPassed).count() * 100.0 / t)
                            : 0;
                    double avgScore = t > 0
                            ? round2(es.stream().mapToDouble(ExamSubmission::getScore).average().orElse(0))
                            : 0.0;
                    return ExamKpiResponse.ExamRow.builder()
                            .examId(e.getId()).examTitle(e.getTitle())
                            .submissions(t).passRate(passRate).avgScore(avgScore)
                            .build();
                })
                .sorted(Comparator.comparingLong(ExamKpiResponse.ExamRow::getSubmissions).reversed())
                .collect(Collectors.toList());
    }

    private List<LabelCount> emptyScoreDistribution() {
        return List.of(
                labelCount("0–49", 0, 0), labelCount("50–69", 0, 0),
                labelCount("70–89", 0, 0), labelCount("90–100", 0, 0));
    }

    // ── Helpers de distribución (LabelCount) ──────────────────────────────

    private List<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toList());
    }

    /** Construye LabelCounts en el orden dado, rellenando con 0 las categorías ausentes. */
    private List<LabelCount> orderedLabelCounts(List<String> orderedLabels, Map<String, Long> counts, long total) {
        return orderedLabels.stream()
                .map(label -> labelCount(label, counts.getOrDefault(label, 0L), total))
                .collect(Collectors.toList());
    }

    private List<LabelCount> zeroLabelCounts(List<String> orderedLabels) {
        return orderedLabels.stream()
                .map(label -> labelCount(label, 0L, 0L))
                .collect(Collectors.toList());
    }

    private LabelCount labelCount(String label, long count, long total) {
        return LabelCount.builder()
                .label(label)
                .count(count)
                .percentage(total > 0 ? (int) Math.round(count * 100.0 / total) : 0)
                .build();
    }

    // ── Builder principal ─────────────────────────────────────────────────

    private KpiSummaryResponse buildSummary(List<Task> tasks, String context, String contextId) {
        if (tasks.isEmpty()) {
            return emptyResponse(context, contextId);
        }

        List<Task> closed    = closedTasks(tasks);
        List<Task> completed = completedTasks(tasks);

        double otr  = computeOnTimeRate(closed);
        double rwr  = computeReworkRate(tasks);
        double qsc  = computeQualityScore(tasks);
        double igeo = computeIgeo(otr, rwr, qsc);

        List<Task> last10 = last10ClosedByCreatedAt(closed);

        // KPI Capacitación: % COMPLETADAS en la sucursal (solo aplica en contexto STORE)
        double trainingRate = 0.0;
        if ("STORE".equals(context)) {
            long totalTrainings = trainingRepository.countByStoreIdAndActivoTrue(contextId);
            if (totalTrainings > 0) {
                long completedTrainings = trainingRepository
                        .countByStoreIdAndProgress_StatusAndActivoTrue(contextId, TrainingStatus.COMPLETADA);
                trainingRate = round2(completedTrainings * 100.0 / totalTrainings);
            }
        }

        return KpiSummaryResponse.builder()
                .context(context)
                .contextId(contextId)
                .onTimeRate(round2(otr))
                .delegacionEfectiva(round2(computeDelegacion(completed)))
                .reworkRate(round2(rwr))
                .avgExecutionMinutes(round2(computeAvgExecutionMinutes(completed)))
                .shiftBreakdown(computeShiftBreakdown(tasks))
                .criticalPending(computeCriticalPending(tasks))
                .igeo(round2(igeo))
                .pipelinePending(pipelineCount(context, contextId, tasks, "PENDING"))
                .pipelineInProgress(pipelineCount(context, contextId, tasks, "IN_PROGRESS"))
                .pipelineCompleted(pipelineCount(context, contextId, tasks, "COMPLETED"))
                .pipelineFailed(pipelineCount(context, contextId, tasks, "FAILED"))
                .sparklineOnTime(buildOnTimeSparkline(last10))
                .sparklineIgeo(buildIgeoSparkline(last10))
                .avgQualityRating(round2(computeQualityRatingAvg(tasks)))
                .trainingCompletionRate(trainingRate)
                .build();
    }

    // ── Fórmulas KPI ──────────────────────────────────────────────────────

    /** KPI #1 — On-Time Rate: % de tareas cerradas que se completaron a tiempo. */
    private double computeOnTimeRate(List<Task> closedTasks) {
        if (closedTasks.isEmpty()) return -1.0;
        long onTimeCount = closedTasks.stream()
                .filter(t -> Boolean.TRUE.equals(t.getExecution().getOnTime()))
                .count();
        return (double) onTimeCount / closedTasks.size() * 100.0;
    }

    /** KPI #2 — Delegación Efectiva: % de tareas COMPLETED sin re-trabajo. */
    private double computeDelegacion(List<Task> completedTasks) {
        if (completedTasks.isEmpty()) return -1.0;
        long noRework = completedTasks.stream()
                .filter(t -> t.getReworkCount() == 0)
                .count();
        return (double) noRework / completedTasks.size() * 100.0;
    }

    /** KPI #3 — Tasa de Re-trabajo: % de tareas con al menos 1 re-trabajo. */
    private double computeReworkRate(List<Task> allTasks) {
        if (allTasks.isEmpty()) return 0.0;
        long withRework = allTasks.stream()
                .filter(t -> t.getReworkCount() > 0)
                .count();
        return (double) withRework / allTasks.size() * 100.0;
    }

    /** KPI #4 — Tiempo Promedio de Ejecución en minutos. */
    private double computeAvgExecutionMinutes(List<Task> completedTasks) {
        List<Task> withTimes = completedTasks.stream()
                .filter(t -> t.getExecution().getStartedAt() != null
                          && t.getExecution().getFinishedAt() != null)
                .collect(Collectors.toList());
        if (withTimes.isEmpty()) return -1.0;
        double avg = withTimes.stream()
                .mapToLong(t -> Duration.between(
                        t.getExecution().getStartedAt(),
                        t.getExecution().getFinishedAt()).toMinutes())
                .average()
                .orElse(-1.0);
        return avg;
    }

    /** KPI #5 — Cumplimiento por Turno: agrupa tareas cerradas por shift. */
    private List<ShiftBreakdownResponse> computeShiftBreakdown(List<Task> allTasks) {
        Map<String, List<Task>> byShift = allTasks.stream()
                .filter(t -> t.getShift() != null)
                .collect(Collectors.groupingBy(Task::getShift));

        return byShift.entrySet().stream()
                .map(e -> {
                    List<Task> shiftClosed = closedTasks(e.getValue());
                    int onTimeCount = (int) shiftClosed.stream()
                            .filter(t -> Boolean.TRUE.equals(t.getExecution().getOnTime()))
                            .count();
                    double otr = shiftClosed.isEmpty()
                            ? -1.0
                            : (double) onTimeCount / shiftClosed.size() * 100.0;
                    return ShiftBreakdownResponse.builder()
                            .shift(e.getKey())
                            .onTimeRate(round2(otr))
                            .totalClosed(shiftClosed.size())
                            .onTimeCount(onTimeCount)
                            .build();
                })
                .sorted(Comparator.comparing(ShiftBreakdownResponse::getShift))
                .collect(Collectors.toList());
    }

    /** KPI #8 — Críticas No Ejecutadas: críticas en PENDING o FAILED. */
    private int computeCriticalPending(List<Task> allTasks) {
        return (int) allTasks.stream()
                .filter(t -> t.isCritical()
                        && (t.getExecution().getStatus() == TaskStatus.PENDING
                            || t.getExecution().getStatus() == TaskStatus.FAILED))
                .count();
    }

    /**
     * KPI #10 — IGEO: Índice Global de Ejecución Operacional.
     * Fórmula: otr*0.5 + (100-rwr)*0.3 + qScore*0.2
     * Retorna -1.0 si otr < 0 (sin datos de On-Time).
     */
    private double computeIgeo(double otr, double rwr, double qScore) {
        if (otr < 0) return -1.0;
        double effectiveQ = (qScore < 0) ? 50.0 : qScore;
        return otr * 0.5 + (100.0 - rwr) * 0.3 + effectiveQ * 0.2;
    }

    /**
     * Pipeline count: uses MongoDB aggregation for STORE context (efficient),
     * falls back to in-memory count for USER context (small dataset).
     */
    private long pipelineCount(String context, String contextId, List<Task> tasks, String status) {
        if ("STORE".equals(context)) {
            // Use cached aggregation result
            return getStorePipelineCounts(contextId).getOrDefault(status, 0L);
        }
        // Fallback for USER context — in-memory from already-loaded tasks
        TaskStatus ts = TaskStatus.valueOf(status);
        return countByStatus(tasks, ts);
    }

    /** Lazy-loaded aggregation result per store, cached within the method call scope. */
    private Map<String, Long> getStorePipelineCounts(String storeId) {
        List<StatusCount> counts = taskRepository.countByStoreGroupedByStatus(storeId);
        Map<String, Long> map = new HashMap<>();
        for (StatusCount sc : counts) {
            if (sc.getId() != null) {
                map.put(sc.getId(), sc.getCount());
            }
        }
        return map;
    }

    // ── Helpers auxiliares ────────────────────────────────────────────────

    private List<Task> closedTasks(List<Task> tasks) {
        return tasks.stream()
                .filter(t -> t.getExecution().getStatus() == TaskStatus.COMPLETED
                          || t.getExecution().getStatus() == TaskStatus.FAILED)
                .collect(Collectors.toList());
    }

    private List<Task> completedTasks(List<Task> tasks) {
        return tasks.stream()
                .filter(t -> t.getExecution().getStatus() == TaskStatus.COMPLETED)
                .collect(Collectors.toList());
    }

    /** Score de calidad normalizado a 0-100 para uso en IGEO. -1.0 si sin datos. */
    private double computeQualityScore(List<Task> tasks) {
        double avg = computeQualityRatingAvg(tasks);
        if (avg < 0) return -1.0;
        return avg / 5.0 * 100.0;
    }

    /** Promedio raw de qualityRating (1.0-5.0). -1.0 si ninguna tarea tiene rating. */
    private double computeQualityRatingAvg(List<Task> tasks) {
        OptionalDouble avg = tasks.stream()
                .filter(t -> t.getQualityRating() != null)
                .mapToDouble(Task::getQualityRating)
                .average();
        return avg.isPresent() ? avg.getAsDouble() : -1.0;
    }

    private long countByStatus(List<Task> tasks, TaskStatus status) {
        return tasks.stream()
                .filter(t -> t.getExecution().getStatus() == status)
                .count();
    }

    /** Últimas 10 tareas cerradas por createdAt ASC (para sparklines). */
    private List<Task> last10ClosedByCreatedAt(List<Task> closedTasks) {
        return closedTasks.stream()
                .filter(t -> t.getCreatedAt() != null)
                .sorted(Comparator.comparing(Task::getCreatedAt).reversed())
                .limit(10)
                .sorted(Comparator.comparing(Task::getCreatedAt))
                .collect(Collectors.toList());
    }

    /** Sparkline On-Time: 100 = a tiempo, 0 = no. */
    private List<Integer> buildOnTimeSparkline(List<Task> last10) {
        return last10.stream()
                .map(t -> Boolean.TRUE.equals(t.getExecution().getOnTime()) ? 100 : 0)
                .collect(Collectors.toList());
    }

    /** Sparkline IGEO rolling: para posición i, IGEO calculado con window=[0..i]. */
    private List<Double> buildIgeoSparkline(List<Task> last10) {
        return IntStream.range(0, last10.size())
                .mapToObj(i -> {
                    List<Task> window = last10.subList(0, i + 1);
                    List<Task> closedW = closedTasks(window);
                    double otr  = computeOnTimeRate(closedW);
                    double rwr  = computeReworkRate(window);
                    double qsc  = computeQualityScore(window);
                    double igeo = computeIgeo(otr, rwr, qsc);
                    return round2(Math.max(igeo, 0.0));
                })
                .collect(Collectors.toList());
    }

    private KpiSummaryResponse emptyResponse(String context, String contextId) {
        return KpiSummaryResponse.builder()
                .context(context)
                .contextId(contextId)
                .onTimeRate(-1.0)
                .delegacionEfectiva(-1.0)
                .reworkRate(0.0)
                .avgExecutionMinutes(-1.0)
                .shiftBreakdown(Collections.emptyList())
                .criticalPending(0)
                .igeo(-1.0)
                .pipelinePending(0)
                .pipelineInProgress(0)
                .pipelineCompleted(0)
                .pipelineFailed(0)
                .sparklineOnTime(Collections.emptyList())
                .sparklineIgeo(Collections.emptyList())
                .avgQualityRating(-1.0)
                .trainingCompletionRate(0.0)
                .build();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
