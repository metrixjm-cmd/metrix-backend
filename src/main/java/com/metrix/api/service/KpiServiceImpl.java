package com.metrix.api.service;

import com.metrix.api.dto.CorrectionSpeedResponse;
import com.metrix.api.dto.KpiSummaryResponse;
import com.metrix.api.dto.ShiftBreakdownResponse;
import com.metrix.api.dto.StoreRankingResponse;
import com.metrix.api.dto.UserResponsibilityResponse;
import com.metrix.api.model.StatusTransition;
import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.model.User;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
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

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    // ── Puntos de entrada ─────────────────────────────────────────────────

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

    @Override
    public List<StoreRankingResponse> getStoreRanking() {
        List<Task> all = taskRepository.findByActivoTrue();
        Map<String, List<Task>> byStore = all.stream()
                .collect(Collectors.groupingBy(Task::getStoreId));

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

    @Override
    public List<UserResponsibilityResponse> getUsersResponsibility(String storeId) {
        List<User> users = userRepository.findByStoreIdAndActivoTrue(storeId);

        List<UserResponsibilityResponse> result = users.stream()
                .map(user -> {
                    List<Task> tasks = taskRepository.findByAssignedUserIdAndActivoTrue(user.getId());
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
        List<double[]> durations = new ArrayList<>();

        for (int i = 0; i < transitions.size() - 1; i++) {
            StatusTransition t = transitions.get(i);
            if (t.getToStatus() == TaskStatus.FAILED) {
                // Buscar la siguiente transición que llega a COMPLETED
                for (int j = i + 1; j < transitions.size(); j++) {
                    if (transitions.get(j).getToStatus() == TaskStatus.COMPLETED) {
                        long minutes = Duration.between(t.getChangedAt(),
                                transitions.get(j).getChangedAt()).toMinutes();
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
                .pipelinePending(countByStatus(tasks, TaskStatus.PENDING))
                .pipelineInProgress(countByStatus(tasks, TaskStatus.IN_PROGRESS))
                .pipelineCompleted(countByStatus(tasks, TaskStatus.COMPLETED))
                .pipelineFailed(countByStatus(tasks, TaskStatus.FAILED))
                .sparklineOnTime(buildOnTimeSparkline(last10))
                .sparklineIgeo(buildIgeoSparkline(last10))
                .avgQualityRating(round2(computeQualityRatingAvg(tasks)))
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
                .build();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
