package com.metrix.api.service;

import com.metrix.api.dto.BadgeDTO;
import com.metrix.api.dto.ExamLeaderboardResponse;
import com.metrix.api.dto.GamificationSummaryDTO;
import com.metrix.api.dto.LeaderboardEntryDTO;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.model.ExamSubmission;
import com.metrix.api.model.Role;
import com.metrix.api.model.Store;
import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.model.User;
import com.metrix.api.repository.ExamSubmissionRepository;
import com.metrix.api.repository.StoreRepository;
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

    private final TaskRepository           taskRepository;
    private final UserRepository           userRepository;
    private final ExamSubmissionRepository submissionRepository;
    private final StoreRepository          storeRepository;

    // ── Leaderboard ───────────────────────────────────────────────────────

    @Override
    public List<LeaderboardEntryDTO> getLeaderboard(String storeId, String period) {
        return rankUsers(userRepository.findByStoreIdAndActivoTrue(storeId), storeId, period,
                         "Top Over-all de la sucursal este mes");
    }

    /**
     * Ranking de los ejecutadores a cargo de un gerente concreto.
     * <p>
     * Una sucursal puede tener más de un gerente, y cada uno responde sólo por su
     * plantilla: rankear la sucursal entera le mostraría gente que no dirige.
     * Mismo criterio que {@code KpiServiceImpl.getUsersResponsibilityForManager}.
     */
    @Override
    public List<LeaderboardEntryDTO> getTeamLeaderboard(String storeId, String managerId, String period) {
        List<User> team = userRepository
                .findByStoreIdAndManagerOwnerIdAndActivoTrue(storeId, managerId).stream()
                .filter(u -> u.getRoles() != null && u.getRoles().contains(Role.EJECUTADOR))
                .collect(Collectors.toList());
        return rankUsers(team, storeId, period, "Top Over-all del equipo este mes");
    }

    /**
     * Núcleo del ranking: calcula la entrada de cada usuario, ordena por Over-all
     * y numera desde 1.
     * <p>
     * El promedio de ejecución de referencia se toma siempre de la sucursal completa,
     * incluso cuando se rankea un solo equipo: es la línea base con la que se compara
     * el desempeño, y recortarla al equipo haría que cada gerente se midiera contra
     * una vara distinta.
     */
    private List<LeaderboardEntryDTO> rankUsers(List<User> users, String storeId,
                                                String period, String badgeDescription) {
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

        if (!entries.isEmpty()) {
            applyColaboradorMesBadge(entries, period, entries.get(0).getIgeo(), badgeDescription);
        }

        return entries;
    }

    // ── Leaderboard gerencial (ADMIN) ─────────────────────────────────────

    @Override
    public List<LeaderboardEntryDTO> getGerencialesLeaderboard(String period) {
        List<User> gerentes = userRepository.findByRolesContaining(Role.GERENTE).stream()
                .filter(User::isActivo)
                .toList();
        if (gerentes.isEmpty()) return List.of();

        Instant now         = Instant.now();
        int     days        = "monthly".equalsIgnoreCase(period) ? 30 : 7;
        Instant periodStart = now.minus(days, ChronoUnit.DAYS);
        Instant prevStart   = now.minus(days * 2L, ChronoUnit.DAYS);

        // Batch fetch: 3 queries fijas para toda la cadena (tareas, usuarios, sucursales).
        List<Task> allTasks = taskRepository.findByActivoTrue();
        Map<String, List<Task>> tasksByUser = allTasks.stream()
                .filter(t -> t.getAssignedUserId() != null)
                .collect(Collectors.groupingBy(Task::getAssignedUserId));

        List<User> activeUsers = userRepository.findByActivoTrue();

        Map<String, String> storeNames = storeRepository.findAll().stream()
                .collect(Collectors.toMap(Store::getId, Store::getNombre, (a, b) -> a));

        // VELOCIDAD_RAYO compara contra el promedio de la propia sucursal, no el de
        // la cadena: sucursales con volúmenes distintos no son comparables entre sí.
        Map<String, Double> avgExecByStore = allTasks.stream()
                .filter(t -> t.getStoreId() != null)
                .collect(Collectors.groupingBy(
                        Task::getStoreId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                ts -> computeAvgExecMin(completedTasks(ts)))));

        Map<String, List<User>> teamByGerente = groupEjecutadoresByGerente(gerentes, activeUsers);

        List<LeaderboardEntryDTO> entries = gerentes.stream()
                .map(g -> {
                    double storeAvgExec = avgExecByStore.getOrDefault(g.getStoreId(), -1.0);
                    LeaderboardEntryDTO entry = buildEntry(
                            g, periodStart, now, prevStart, periodStart, storeAvgExec, tasksByUser);

                    List<User> team = teamByGerente.getOrDefault(g.getId(), List.of());
                    entry.setStoreName(storeNames.getOrDefault(g.getStoreId(), "—"));
                    entry.setColaboradorCount(team.size());
                    entry.setTeamAvgIgeo(computeTeamAvgIgeo(team, tasksByUser, periodStart, now));
                    return entry;
                })
                .sorted(POR_DESEMPENO_DE_EQUIPO)
                .collect(Collectors.toList());

        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRank(i + 1);
        }

        // El top-1 gerencial se premia por el desempeño de su equipo, que es el
        // criterio con el que se le rankea.
        double topTeamIgeo = entries.get(0).getTeamAvgIgeo();
        applyColaboradorMesBadge(entries, period, topTeamIgeo,
                                 "Mejor equipo de la cadena este mes");

        return entries;
    }

    /**
     * Orden del ranking gerencial: manda el IGEO del equipo.
     * <p>
     * A un gerente se le mide por lo que produce su equipo, no por las pocas tareas
     * que ejecute en persona — ordenar por su IGEO propio deja a casi todos
     * empatados en cero. Los equipos sin datos (-1.0) caen al final de forma
     * natural. Se desempata por IGEO propio y, en última instancia, por nombre
     * para que el orden sea estable entre llamadas.
     */
    private static final Comparator<LeaderboardEntryDTO> POR_DESEMPENO_DE_EQUIPO = Comparator
            .<LeaderboardEntryDTO>comparingDouble(LeaderboardEntryDTO::getTeamAvgIgeo).reversed()
            .thenComparing(Comparator.<LeaderboardEntryDTO>comparingDouble(LeaderboardEntryDTO::getIgeo).reversed())
            .thenComparing(LeaderboardEntryDTO::getNombre, Comparator.nullsLast(String::compareTo));

    /**
     * Asocia cada ejecutador activo con su gerente.
     * <p>
     * Vínculo primario: {@code managerOwnerId}; fallback por
     * {@code managerOwnerNumeroUsuario} cuando el ObjectId no está poblado.
     * Los ejecutadores sin gerente asignado (datos legacy) se atribuyen a los
     * gerentes de su misma sucursal que quedaron sin equipo — si una sucursal
     * tiene varios gerentes en esa situación, todos comparten el mismo pool.
     */
    private Map<String, List<User>> groupEjecutadoresByGerente(List<User> gerentes,
                                                               List<User> activeUsers) {
        List<User> ejecutadores = activeUsers.stream()
                .filter(u -> u.getRoles() != null && u.getRoles().contains(Role.EJECUTADOR))
                .toList();

        Set<String> gerenteIds = gerentes.stream().map(User::getId).collect(Collectors.toSet());
        Map<String, String> gerenteIdByNumero = gerentes.stream()
                .filter(g -> g.getNumeroUsuario() != null)
                .collect(Collectors.toMap(User::getNumeroUsuario, User::getId, (a, b) -> a));

        Map<String, List<User>> teams     = new HashMap<>();
        List<User>              huerfanos = new ArrayList<>();

        for (User e : ejecutadores) {
            String ownerId = null;
            if (e.getManagerOwnerId() != null && gerenteIds.contains(e.getManagerOwnerId())) {
                ownerId = e.getManagerOwnerId();
            } else if (e.getManagerOwnerNumeroUsuario() != null) {
                ownerId = gerenteIdByNumero.get(e.getManagerOwnerNumeroUsuario());
            }

            if (ownerId != null) {
                teams.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(e);
            } else {
                huerfanos.add(e);
            }
        }

        Map<String, List<User>> huerfanosByStore = huerfanos.stream()
                .filter(u -> u.getStoreId() != null)
                .collect(Collectors.groupingBy(User::getStoreId));

        for (User g : gerentes) {
            if (teams.containsKey(g.getId())) continue;
            List<User> pool = huerfanosByStore.get(g.getStoreId());
            if (pool != null && !pool.isEmpty()) {
                teams.put(g.getId(), List.copyOf(pool));
            }
        }

        return teams;
    }

    /**
     * IGEO promedio del equipo dentro del período solicitado.
     * <p>
     * Se acota a la misma ventana que el IGEO propio del gerente. Calcularlo sobre
     * el historial completo lo dejaba insensible al período: el ranking no cambiaba
     * entre 7 y 30 días, y contradecía al ranking de colaboradores de la misma
     * sucursal, que sí filtra por ventana.
     * <p>
     * Excluye a los miembros sin tareas cerradas en el período: promediarlos como 0
     * hundiría el indicador de equipos con altas recientes.
     *
     * @return promedio redondeado, o -1.0 si ningún miembro tiene datos
     */
    private double computeTeamAvgIgeo(List<User> team, Map<String, List<Task>> tasksByUser,
                                      Instant periodStart, Instant periodEnd) {
        OptionalDouble avg = team.stream()
                .mapToDouble(u -> computeUserIgeo(
                        filterByPeriod(tasksByUser.getOrDefault(u.getId(), List.of()),
                                       periodStart, periodEnd)))
                .filter(igeo -> igeo >= 0)
                .average();
        return avg.isPresent() ? round2(avg.getAsDouble()) : -1.0;
    }

    /**
     * Otorga COLABORADOR_MES al top-1 del ranking, sólo en el período mensual.
     *
     * @param topScore    puntaje con el que se rankeó al primero; sin puntaje
     *                    positivo no hay insignia (evita premiar un ranking vacío)
     * @param description texto de la insignia, distinto según se rankee a
     *                    colaboradores por su IGEO o a gerentes por el de su equipo
     */
    private void applyColaboradorMesBadge(List<LeaderboardEntryDTO> entries, String period,
                                          double topScore, String description) {
        if (!"monthly".equalsIgnoreCase(period) || entries.isEmpty()) return;
        if (topScore <= 0) return;

        LeaderboardEntryDTO top = entries.get(0);

        List<BadgeDTO> updated = new ArrayList<>(top.getBadges());
        boolean alreadyHas = updated.stream()
                .anyMatch(b -> "COLABORADOR_MES".equals(b.getType()));
        if (alreadyHas) return;

        updated.add(BadgeDTO.builder()
                .type("COLABORADOR_MES")
                .title("Colaborador del Mes")
                .description(description)
                .icon("🥇")
                .earnedAt(Instant.now())
                .build());
        top.setBadges(updated);
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

    // ── Ranking de exámenes ───────────────────────────────────────────────

    @Override
    public ExamLeaderboardResponse getExamLeaderboard(String requesterNumeroUsuario) {
        User requester = userRepository.findByNumeroUsuario(requesterNumeroUsuario)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + requesterNumeroUsuario));

        boolean isAdmin   = requester.getRoles().contains(Role.ADMIN);
        boolean isGerente = requester.getRoles().contains(Role.GERENTE);

        // Mapa de stores para resolver nombres sin N+1
        Map<String, String> storeNames = storeRepository.findAll().stream()
                .collect(Collectors.toMap(Store::getId, Store::getNombre, (a, b) -> a));

        List<User> targetUsers;
        List<ExamSubmission> submissions;

        if (isAdmin) {
            // ADMIN → ve ranking de todos los GERENTEs
            targetUsers = userRepository.findByRolesContaining(Role.GERENTE);
            List<String> ids = targetUsers.stream().map(User::getId).toList();
            submissions = ids.isEmpty() ? List.of() : submissionRepository.findByUserIdIn(ids);

        } else if (isGerente) {
            // GERENTE → ve ranking de EJECUTADOREs de su sucursal
            targetUsers = userRepository.findByStoreIdAndActivoTrue(requester.getStoreId())
                    .stream()
                    .filter(u -> u.getRoles().contains(Role.EJECUTADOR))
                    .toList();
            List<String> ids = targetUsers.stream().map(User::getId).toList();
            submissions = ids.isEmpty() ? List.of() : submissionRepository.findByUserIdIn(ids);

        } else {
            // EJECUTADOR → ve ranking de todos los participantes de su sucursal
            submissions = submissionRepository.findByStoreId(requester.getStoreId());
            Set<String> participantIds = submissions.stream()
                    .map(ExamSubmission::getUserId).collect(Collectors.toSet());
            targetUsers = userRepository.findByStoreIdAndActivoTrue(requester.getStoreId())
                    .stream()
                    .filter(u -> participantIds.contains(u.getId()))
                    .toList();
        }

        // Agrupar submissions por userId
        Map<String, List<ExamSubmission>> byUser = submissions.stream()
                .collect(Collectors.groupingBy(ExamSubmission::getUserId));

        // Mapa de users para lookup rápido
        Map<String, User> userMap = targetUsers.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        // Construir entradas — incluir solo usuarios con al menos 1 submission
        List<ExamLeaderboardResponse.ExamRankEntry> entries = byUser.entrySet().stream()
                .filter(e -> userMap.containsKey(e.getKey()))
                .map(e -> {
                    User u = userMap.get(e.getKey());
                    List<ExamSubmission> subs = e.getValue();
                    int  taken  = subs.size();
                    int  passed = (int) subs.stream().filter(ExamSubmission::isPassed).count();
                    double avg  = subs.stream().mapToDouble(ExamSubmission::getScore).average().orElse(0);
                    int  rate   = taken > 0 ? (int) Math.round((passed * 100.0) / taken) : 0;
                    String role = u.getRoles().stream().findFirst().map(Enum::name).orElse("—");
                    return ExamLeaderboardResponse.ExamRankEntry.builder()
                            .userId(u.getId())
                            .userName(u.getNombre())
                            .userNumero(u.getNumeroUsuario())
                            .role(role)
                            .storeId(u.getStoreId())
                            .storeName(storeNames.getOrDefault(u.getStoreId(), "—"))
                            .avgScore(Math.round(avg * 10.0) / 10.0)
                            .examsTaken(taken)
                            .examsPassed(passed)
                            .passRate(rate)
                            .build();
                })
                .sorted(Comparator.comparingDouble(ExamLeaderboardResponse.ExamRankEntry::getAvgScore).reversed())
                .toList();

        // Asignar rank (1-based, empate comparte rank)
        List<ExamLeaderboardResponse.ExamRankEntry> ranked = new ArrayList<>();
        int rank = 1;
        for (int i = 0; i < entries.size(); i++) {
            ExamLeaderboardResponse.ExamRankEntry entry = entries.get(i);
            if (i > 0 && entry.getAvgScore() < entries.get(i - 1).getAvgScore()) rank = i + 1;
            ranked.add(ExamLeaderboardResponse.ExamRankEntry.builder()
                    .rank(rank)
                    .userId(entry.getUserId())
                    .userName(entry.getUserName())
                    .userNumero(entry.getUserNumero())
                    .role(entry.getRole())
                    .storeId(entry.getStoreId())
                    .storeName(entry.getStoreName())
                    .avgScore(entry.getAvgScore())
                    .examsTaken(entry.getExamsTaken())
                    .examsPassed(entry.getExamsPassed())
                    .passRate(entry.getPassRate())
                    .build());
        }

        // Entrada del usuario actual (relevante para GERENTE y EJECUTADOR)
        ExamLeaderboardResponse.ExamRankEntry myEntry = ranked.stream()
                .filter(e -> e.getUserId().equals(requester.getId()))
                .findFirst().orElse(null);

        return ExamLeaderboardResponse.builder()
                .ranking(ranked)
                .myEntry(myEntry)
                .totalParticipants(ranked.size())
                .build();
    }
}
