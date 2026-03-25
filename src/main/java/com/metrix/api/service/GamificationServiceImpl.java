package com.metrix.api.service;

import com.metrix.api.dto.BadgeDTO;
import com.metrix.api.dto.GamificationSummaryDTO;
import com.metrix.api.dto.LeaderboardEntryDTO;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.model.User;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementación del módulo de Gamificación METRIX — Sprint 12.
 * <p>
 * Insignias disponibles (todas computed on-the-fly, sin persistencia):
 * <ul>
 *   <li>PUNTUAL_ELITE     — OnTimeRate ≥ 95% con mínimo 10 tareas cerradas</li>
 *   <li>CERO_RETRABAJOS   — ReworkRate = 0% con mínimo 5 tareas</li>
 *   <li>VELOCIDAD_RAYO    — AvgExecMin ≤ 50% del promedio de la sucursal</li>
 *   <li>COLABORADOR_MES   — Rank #1 del leaderboard mensual</li>
 *   <li>RACHA_7           — 7 o más tareas completadas en los últimos 7 días</li>
 * </ul>
 * <p>
 * Inyecta {@link TaskRepository} y {@link UserRepository} directamente
 * para evitar dependencia circular con {@link KpiService}.
 */
@Service
@RequiredArgsConstructor
public class GamificationServiceImpl implements GamificationService {

    private static final int TOTAL_BADGES = 5;

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    // ── Leaderboard ───────────────────────────────────────────────────────

    @Override
    public List<LeaderboardEntryDTO> getLeaderboard(String storeId, String period) {
        List<User> users = userRepository.findByStoreIdAndActivoTrue(storeId);

        Instant now          = Instant.now();
        int     days         = "monthly".equalsIgnoreCase(period) ? 30 : 7;
        Instant periodStart  = now.minus(days, ChronoUnit.DAYS);
        Instant prevStart    = now.minus(days * 2L, ChronoUnit.DAYS);

        // Batch fetch: 1 query para TODAS las tareas (elimina N+1 en buildEntry)
        List<Task> allStoreTasks = taskRepository.findByStoreIdAndActivoTrue(storeId);
        Map<String, List<Task>> tasksByUser = allStoreTasks.stream()
                .filter(t -> t.getAssignedUserId() != null)
                .collect(Collectors.groupingBy(Task::getAssignedUserId));
        double storeAvgExec = computeAvgExecMin(completedTasks(allStoreTasks));

        List<LeaderboardEntryDTO> entries = users.stream()
                .map(u -> buildEntry(u, periodStart, now, prevStart, periodStart, storeAvgExec, tasksByUser))
                .sorted(Comparator.comparingDouble(LeaderboardEntryDTO::getIgeo).reversed())
                .collect(Collectors.toList());

        // Rank 1-based
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRank(i + 1);
        }

        // Badge COLABORADOR_MES sólo en leaderboard mensual al top-1
        if ("monthly".equalsIgnoreCase(period) && !entries.isEmpty()) {
            LeaderboardEntryDTO top = entries.get(0);
            if (top.getIgeo() > 0) {
                List<BadgeDTO> updated = new ArrayList<>(top.getBadges());
                boolean alreadyHas = updated.stream()
                        .anyMatch(b -> "COLABORADOR_MES".equals(b.getType()));
                if (!alreadyHas) {
                    updated.add(BadgeDTO.builder()
                            .type("COLABORADOR_MES")
                            .title("Colaborador del Mes")
                            .description("Top IGEO de la sucursal este mes")
                            .icon("🥇")
                            .earnedAt(Instant.now())
                            .build());
                    top.setBadges(updated);
                }
            }
        }

        return entries;
    }

    // ── My Gamification ───────────────────────────────────────────────────

    @Override
    public GamificationSummaryDTO getMyGamification(String userId, String storeId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Batch fetch: 1 query para TODAS las tareas de la sucursal (elimina N+1)
        List<Task> allStoreTasks = taskRepository.findByStoreIdAndActivoTrue(storeId);
        Map<String, List<Task>> tasksByUser = allStoreTasks.stream()
                .filter(t -> t.getAssignedUserId() != null)
                .collect(Collectors.groupingBy(Task::getAssignedUserId));

        List<Task> userTasks = tasksByUser.getOrDefault(userId, List.of());

        double storeAvgExec = computeAvgExecMin(completedTasks(allStoreTasks));
        List<BadgeDTO> badges = computeBadges(userTasks, storeAvgExec, false);

        double igeo = Math.max(computeUserIgeo(userTasks), 0.0);

        // Rank: usar el mapa pre-calculado en vez de N queries
        List<User> storeUsers = userRepository.findByStoreIdAndActivoTrue(storeId);
        int rank = 1;
        for (User su : storeUsers) {
            if (su.getId().equals(userId)) continue;
            List<Task> suTasks = tasksByUser.getOrDefault(su.getId(), List.of());
            double suIgeo = Math.max(computeUserIgeo(suTasks), 0.0);
            if (suIgeo > igeo) rank++;
        }

        return GamificationSummaryDTO.builder()
                .userId(userId)
                .nombre(user.getNombre())
                .rank(rank)
                .totalInStore(storeUsers.size())
                .igeo(round2(igeo))
                .badges(badges)
                .earnedBadgesCount(badges.size())
                .availableBadgesCount(TOTAL_BADGES)
                .build();
    }

    // ── Builders privados ─────────────────────────────────────────────────

    private LeaderboardEntryDTO buildEntry(User user,
                                           Instant periodStart, Instant periodEnd,
                                           Instant prevStart,   Instant prevEnd,
                                           double storeAvgExec,
                                           Map<String, List<Task>> tasksByUser) {
        List<Task> allUserTasks   = tasksByUser.getOrDefault(user.getId(), List.of());
        List<Task> periodTasks    = filterByPeriod(allUserTasks, periodStart, periodEnd);
        List<Task> prevTasks      = filterByPeriod(allUserTasks, prevStart, prevEnd);

        List<Task> periodClosed   = closedTasks(periodTasks);
        List<Task> prevClosed     = closedTasks(prevTasks);

        double otr  = computeOnTimeRate(periodClosed);
        double rwr  = computeReworkRate(periodTasks);
        double qsc  = computeQualityScore(periodTasks);
        double igeo = Math.max(computeIgeo(otr, rwr, qsc), 0.0);

        double prevOtr  = computeOnTimeRate(prevClosed);
        double prevRwr  = computeReworkRate(prevTasks);
        double prevQsc  = computeQualityScore(prevTasks);
        double prevIgeo = Math.max(computeIgeo(prevOtr, prevRwr, prevQsc), 0.0);

        double igeoChange = (prevIgeo > 0) ? round2(igeo - prevIgeo) : 0.0;

        List<BadgeDTO> badges = computeBadges(allUserTasks, storeAvgExec, false);

        return LeaderboardEntryDTO.builder()
                .rank(0)
                .userId(user.getId())
                .nombre(user.getNombre())
                .puesto(user.getPuesto())
                .turno(user.getTurno())
                .igeo(round2(igeo))
                .igeoChange(igeoChange)
                .totalTasks(periodTasks.size())
                .completedTasks((int) countByStatus(periodTasks, TaskStatus.COMPLETED))
                .onTimeRate(round2(otr))
                .badges(badges)
                .build();
    }

    // ── Lógica de insignias ───────────────────────────────────────────────

    private List<BadgeDTO> computeBadges(List<Task> userTasks, double storeAvgExec,
                                         boolean isColaboradorMes) {
        List<BadgeDTO> badges    = new ArrayList<>();
        List<Task>     closed    = closedTasks(userTasks);
        List<Task>     completed = completedTasks(userTasks);

        double otr     = computeOnTimeRate(closed);
        double rwr     = computeReworkRate(userTasks);
        double avgExec = computeAvgExecMin(completed);

        // PUNTUAL_ELITE: OnTimeRate >= 95% con mínimo 10 tareas cerradas
        if (closed.size() >= 10 && otr >= 95.0) {
            badges.add(BadgeDTO.builder()
                    .type("PUNTUAL_ELITE")
                    .title("Puntual Elite")
                    .description("95% o más de tareas completadas a tiempo")
                    .icon("⏱️")
                    .earnedAt(Instant.now())
                    .build());
        }

        // CERO_RETRABAJOS: 0% rework con mínimo 5 tareas
        if (userTasks.size() >= 5 && rwr == 0.0) {
            badges.add(BadgeDTO.builder()
                    .type("CERO_RETRABAJOS")
                    .title("Cero Retrabajos")
                    .description("Sin tareas devueltas por mala ejecución")
                    .icon("✅")
                    .earnedAt(Instant.now())
                    .build());
        }

        // VELOCIDAD_RAYO: avgExecMin <= 50% del promedio de la sucursal
        if (avgExec > 0 && storeAvgExec > 0 && avgExec <= storeAvgExec * 0.5) {
            badges.add(BadgeDTO.builder()
                    .type("VELOCIDAD_RAYO")
                    .title("Velocidad Rayo")
                    .description("Tiempo de ejecución 50% menor al promedio de la sucursal")
                    .icon("⚡")
                    .earnedAt(Instant.now())
                    .build());
        }

        // COLABORADOR_MES: asignado externamente por el leaderboard mensual
        if (isColaboradorMes) {
            badges.add(BadgeDTO.builder()
                    .type("COLABORADOR_MES")
                    .title("Colaborador del Mes")
                    .description("Top IGEO de la sucursal este mes")
                    .icon("🥇")
                    .earnedAt(Instant.now())
                    .build());
        }

        // RACHA_7: 7 o más tareas completadas en los últimos 7 días
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long recentCompleted = completed.stream()
                .filter(t -> t.getExecution().getFinishedAt() != null
                          && !t.getExecution().getFinishedAt().isBefore(sevenDaysAgo))
                .count();
        if (recentCompleted >= 7) {
            badges.add(BadgeDTO.builder()
                    .type("RACHA_7")
                    .title("Racha de 7")
                    .description("7 o más tareas completadas en los últimos 7 días")
                    .icon("🔥")
                    .earnedAt(Instant.now())
                    .build());
        }

        return badges;
    }

    // ── Helpers KPI (independientes de KpiService para evitar ciclo) ──────

    private double computeUserIgeo(List<Task> userTasks) {
        List<Task> closed = closedTasks(userTasks);
        double otr = computeOnTimeRate(closed);
        double rwr = computeReworkRate(userTasks);
        double qsc = computeQualityScore(userTasks);
        return computeIgeo(otr, rwr, qsc);
    }

    private List<Task> filterByPeriod(List<Task> tasks, Instant start, Instant end) {
        return tasks.stream()
                .filter(t -> t.getCreatedAt() != null
                          && !t.getCreatedAt().isBefore(start)
                          && t.getCreatedAt().isBefore(end))
                .collect(Collectors.toList());
    }

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

    private double computeOnTimeRate(List<Task> closedTasks) {
        if (closedTasks.isEmpty()) return -1.0;
        long onTime = closedTasks.stream()
                .filter(t -> Boolean.TRUE.equals(t.getExecution().getOnTime()))
                .count();
        return (double) onTime / closedTasks.size() * 100.0;
    }

    private double computeReworkRate(List<Task> tasks) {
        if (tasks.isEmpty()) return 0.0;
        long withRework = tasks.stream().filter(t -> t.getReworkCount() > 0).count();
        return (double) withRework / tasks.size() * 100.0;
    }

    private double computeAvgExecMin(List<Task> completedTasks) {
        List<Task> withTimes = completedTasks.stream()
                .filter(t -> t.getExecution().getStartedAt() != null
                          && t.getExecution().getFinishedAt() != null)
                .collect(Collectors.toList());
        if (withTimes.isEmpty()) return -1.0;
        return withTimes.stream()
                .mapToLong(t -> Duration.between(
                        t.getExecution().getStartedAt(),
                        t.getExecution().getFinishedAt()).toMinutes())
                .average()
                .orElse(-1.0);
    }

    private double computeQualityScore(List<Task> tasks) {
        OptionalDouble avg = tasks.stream()
                .filter(t -> t.getQualityRating() != null)
                .mapToDouble(Task::getQualityRating)
                .average();
        return avg.isPresent() ? avg.getAsDouble() / 5.0 * 100.0 : -1.0;
    }

    private double computeIgeo(double otr, double rwr, double qScore) {
        if (otr < 0) return -1.0;
        double effectiveQ = (qScore < 0) ? 50.0 : qScore;
        return otr * 0.5 + (100.0 - rwr) * 0.3 + effectiveQ * 0.2;
    }

    private long countByStatus(List<Task> tasks, TaskStatus status) {
        return tasks.stream()
                .filter(t -> t.getExecution().getStatus() == status)
                .count();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
