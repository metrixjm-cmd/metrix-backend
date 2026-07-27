package com.metrix.api.service;

import com.metrix.api.dto.LeaderboardEntryDTO;
import com.metrix.api.model.Execution;
import com.metrix.api.model.Role;
import com.metrix.api.model.Store;
import com.metrix.api.model.Task;
import com.metrix.api.model.TaskStatus;
import com.metrix.api.model.User;
import com.metrix.api.repository.ExamSubmissionRepository;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.TaskRepository;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Leaderboard gerencial (ADMIN) — {@code getGerencialesLeaderboard}.
 * <p>
 * Los IGEO esperados salen de la fórmula {@code otr*0.5 + (100-rwr)*0.3 + q*0.2}
 * con q=50 por defecto cuando no hay calificación de calidad:
 * <ul>
 *   <li>1 tarea COMPLETED on-time  → 100*0.5 + 100*0.3 + 50*0.2 = <b>90.0</b></li>
 *   <li>1 tarea COMPLETED tardía   →   0*0.5 + 100*0.3 + 50*0.2 = <b>40.0</b></li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GamificationGerentesLeaderboardTest {

    private static final String STORE_A = "store-a";
    private static final String STORE_B = "store-b";

    @Mock private TaskRepository           taskRepository;
    @Mock private UserRepository           userRepository;
    @Mock private ExamSubmissionRepository submissionRepository;
    @Mock private StoreRepository          storeRepository;

    @InjectMocks private GamificationServiceImpl service;

    // ── Fixtures ──────────────────────────────────────────────────────────

    private static User user(String id, String nombre, String storeId, Role role) {
        return User.builder()
                .id(id)
                .nombre(nombre)
                .numeroUsuario("USR-" + id)
                .storeId(storeId)
                .turno("MATUTINO")
                .roles(Set.of(role))
                .activo(true)
                .build();
    }

    private static User ejecutador(String id, String storeId, String managerOwnerId) {
        User u = user(id, "Ejecutador " + id, storeId, Role.EJECUTADOR);
        u.setManagerOwnerId(managerOwnerId);
        return u;
    }

    private static Store store(String id, String nombre) {
        return Store.builder().id(id).nombre(nombre).codigo("COD-" + id).build();
    }

    /** Tarea cerrada hace 1 día, dentro de la ventana semanal. */
    private static Task task(String userId, String storeId, boolean onTime) {
        return taskHaceDias(userId, storeId, onTime, 1);
    }

    /** Tarea cerrada hace N días, para ejercitar los bordes de la ventana. */
    private static Task taskHaceDias(String userId, String storeId, boolean onTime, int dias) {
        Instant cuando = Instant.now().minus(dias, ChronoUnit.DAYS);
        return Task.builder()
                .id("task-" + userId + "-" + onTime + "-" + dias)
                .assignedUserId(userId)
                .storeId(storeId)
                .createdAt(cuando)
                .activo(true)
                .reworkCount(0)
                .execution(Execution.builder()
                        .status(TaskStatus.COMPLETED)
                        .startedAt(cuando)
                        .finishedAt(cuando.plus(30, ChronoUnit.MINUTES))
                        .onTime(onTime)
                        .build())
                .build();
    }

    private void mockChain(List<User> gerentes, List<User> allUsers,
                           List<Task> tasks, List<Store> stores) {
        when(userRepository.findByRolesContaining(Role.GERENTE)).thenReturn(gerentes);
        when(userRepository.findByActivoTrue()).thenReturn(allUsers);
        when(taskRepository.findByActivoTrue()).thenReturn(tasks);
        when(storeRepository.findAll()).thenReturn(stores);
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    void ordenaGerentesPorIgeoDeEquipoYAsignaRank1Based() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User g2 = user("g2", "Gerente Dos", STORE_B, Role.GERENTE);
        User e1 = ejecutador("e1", STORE_A, "g1");   // equipo de g1 → 40.0
        User e2 = ejecutador("e2", STORE_B, "g2");   // equipo de g2 → 90.0

        mockChain(
                List.of(g1, g2),
                List.of(g1, g2, e1, e2),
                List.of(task("e1", STORE_A, false), task("e2", STORE_B, true)),
                List.of(store(STORE_A, "Sucursal Centro"), store(STORE_B, "Sucursal Norte")));

        List<LeaderboardEntryDTO> ranking = service.getGerencialesLeaderboard("weekly");

        assertEquals(2, ranking.size());
        assertEquals("g2", ranking.get(0).getUserId());
        assertEquals(1,    ranking.get(0).getRank());
        assertEquals(90.0, ranking.get(0).getTeamAvgIgeo());
        assertEquals("g1", ranking.get(1).getUserId());
        assertEquals(2,    ranking.get(1).getRank());
        assertEquals(40.0, ranking.get(1).getTeamAvgIgeo());
    }

    @Test
    void elIgeoPropioDelGerenteNoAlteraElOrden() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User g2 = user("g2", "Gerente Dos", STORE_B, Role.GERENTE);
        User e1 = ejecutador("e1", STORE_A, "g1");   // equipo flojo  → 40.0
        User e2 = ejecutador("e2", STORE_B, "g2");   // equipo fuerte → 90.0

        // g1 ejecuta impecable en persona (90.0) pero su equipo rinde peor:
        // el ranking mide al gerente por su equipo, así que g2 va primero.
        mockChain(
                List.of(g1, g2),
                List.of(g1, g2, e1, e2),
                List.of(task("g1", STORE_A, true), task("e1", STORE_A, false),
                        task("e2", STORE_B, true)),
                List.of(store(STORE_A, "Sucursal Centro"), store(STORE_B, "Sucursal Norte")));

        List<LeaderboardEntryDTO> ranking = service.getGerencialesLeaderboard("weekly");

        assertEquals("g2", ranking.get(0).getUserId(), "gana el mejor equipo");
        assertEquals(90.0, byId(ranking, "g1").getIgeo(), "el IGEO propio se conserva como contexto");
    }

    @Test
    void equiposSinDatosQuedanAlFinalDelRanking() {
        User conEquipo = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User sinEquipo = user("g2", "Gerente Dos", STORE_B, Role.GERENTE);
        User e1 = ejecutador("e1", STORE_A, "g1");

        // g2 tiene IGEO propio alto, pero su equipo no reporta datos (-1.0).
        mockChain(
                List.of(conEquipo, sinEquipo),
                List.of(conEquipo, sinEquipo, e1),
                List.of(task("g2", STORE_B, true), task("e1", STORE_A, false)),
                List.of(store(STORE_A, "Sucursal Centro"), store(STORE_B, "Sucursal Norte")));

        List<LeaderboardEntryDTO> ranking = service.getGerencialesLeaderboard("weekly");

        assertEquals("g1", ranking.get(0).getUserId());
        assertEquals("g2", ranking.get(1).getUserId(), "sin datos de equipo se va al final");
    }

    @Test
    void resuelveNombreDeSucursalDeCadaGerente() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);

        mockChain(List.of(g1), List.of(g1), List.of(task("g1", STORE_A, true)),
                  List.of(store(STORE_A, "Sucursal Centro")));

        assertEquals("Sucursal Centro", service.getGerencialesLeaderboard("weekly").get(0).getStoreName());
    }

    @Test
    void cuentaEquipoYPromediaIgeoSegunManagerOwnerId() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User e1 = ejecutador("e1", STORE_A, "g1");   // 90.0
        User e2 = ejecutador("e2", STORE_A, "g1");   // 40.0

        mockChain(
                List.of(g1),
                List.of(g1, e1, e2),
                List.of(task("g1", STORE_A, true), task("e1", STORE_A, true), task("e2", STORE_A, false)),
                List.of(store(STORE_A, "Sucursal Centro")));

        LeaderboardEntryDTO entry = service.getGerencialesLeaderboard("weekly").get(0);

        assertEquals(2, entry.getColaboradorCount());
        assertEquals(65.0, entry.getTeamAvgIgeo(), "promedio de 90.0 y 40.0");
    }

    @Test
    void noAtribuyeEjecutadoresDeOtroGerente() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User g2 = user("g2", "Gerente Dos", STORE_A, Role.GERENTE);
        User e1 = ejecutador("e1", STORE_A, "g1");
        User e2 = ejecutador("e2", STORE_A, "g2");
        User e3 = ejecutador("e3", STORE_A, "g2");

        mockChain(
                List.of(g1, g2),
                List.of(g1, g2, e1, e2, e3),
                List.of(task("g1", STORE_A, true), task("g2", STORE_A, true)),
                List.of(store(STORE_A, "Sucursal Centro")));

        List<LeaderboardEntryDTO> ranking = service.getGerencialesLeaderboard("weekly");

        assertEquals(1, byId(ranking, "g1").getColaboradorCount());
        assertEquals(2, byId(ranking, "g2").getColaboradorCount());
    }

    @Test
    void datosLegacySinManagerOwnerCaenAlGerenteDeLaMismaSucursal() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User huerfano = ejecutador("e1", STORE_A, null);
        User ajeno    = ejecutador("e2", STORE_B, null);   // otra sucursal: no cuenta

        mockChain(
                List.of(g1),
                List.of(g1, huerfano, ajeno),
                List.of(task("g1", STORE_A, true), task("e1", STORE_A, true)),
                List.of(store(STORE_A, "Sucursal Centro")));

        LeaderboardEntryDTO entry = service.getGerencialesLeaderboard("weekly").get(0);

        assertEquals(1, entry.getColaboradorCount());
        assertEquals(90.0, entry.getTeamAvgIgeo());
    }

    @Test
    void promedioDeEquipoExcluyeMiembrosSinTareas() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User conDatos = ejecutador("e1", STORE_A, "g1");   // 90.0
        User reciente = ejecutador("e2", STORE_A, "g1");   // sin tareas

        mockChain(
                List.of(g1),
                List.of(g1, conDatos, reciente),
                List.of(task("g1", STORE_A, true), task("e1", STORE_A, true)),
                List.of(store(STORE_A, "Sucursal Centro")));

        LeaderboardEntryDTO entry = service.getGerencialesLeaderboard("weekly").get(0);

        assertEquals(2, entry.getColaboradorCount(), "el alta reciente sí cuenta en la plantilla");
        assertEquals(90.0, entry.getTeamAvgIgeo(), "pero no arrastra el promedio a la baja");
    }

    @Test
    void equipoSinDatosDevuelveMenosUnoParaQueLaUiLoDistingaDeCero() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User sinDatos = ejecutador("e1", STORE_A, "g1");

        mockChain(
                List.of(g1),
                List.of(g1, sinDatos),
                List.of(task("g1", STORE_A, true)),
                List.of(store(STORE_A, "Sucursal Centro")));

        LeaderboardEntryDTO entry = service.getGerencialesLeaderboard("weekly").get(0);

        assertEquals(1, entry.getColaboradorCount());
        assertEquals(-1.0, entry.getTeamAvgIgeo());
    }

    @Test
    void promedioDeEquipoIgnoraTareasFueraDelPeriodo() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User e1 = ejecutador("e1", STORE_A, "g1");

        // Única tarea del equipo: 40 días atrás, fuera de las ventanas de 7 y 30 días.
        mockChain(
                List.of(g1),
                List.of(g1, e1),
                List.of(taskHaceDias("e1", STORE_A, true, 40)),
                List.of(store(STORE_A, "Sucursal Centro")));

        assertEquals(-1.0, service.getGerencialesLeaderboard("weekly").get(0).getTeamAvgIgeo(),
                     "una tarea de hace 40 días no puede puntuar en la ventana semanal");
        assertEquals(-1.0, service.getGerencialesLeaderboard("monthly").get(0).getTeamAvgIgeo(),
                     "ni en la mensual");
    }

    @Test
    void promedioDeEquipoCambiaEntreSemanalYMensual() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User e1 = ejecutador("e1", STORE_A, "g1");

        // Fuera de los 7 días pero dentro de los 30: el período debe notarse.
        mockChain(
                List.of(g1),
                List.of(g1, e1),
                List.of(taskHaceDias("e1", STORE_A, true, 15)),
                List.of(store(STORE_A, "Sucursal Centro")));

        assertEquals(-1.0, service.getGerencialesLeaderboard("weekly").get(0).getTeamAvgIgeo());
        assertEquals(90.0, service.getGerencialesLeaderboard("monthly").get(0).getTeamAvgIgeo());
    }

    @Test
    void gerenteSinEquipoReportaCeroColaboradores() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);

        mockChain(List.of(g1), List.of(g1), List.of(task("g1", STORE_A, true)),
                  List.of(store(STORE_A, "Sucursal Centro")));

        LeaderboardEntryDTO entry = service.getGerencialesLeaderboard("weekly").get(0);

        assertEquals(0, entry.getColaboradorCount());
        assertEquals(-1.0, entry.getTeamAvgIgeo());
    }

    @Test
    void ignoraGerentesInactivos() {
        User activo   = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User inactivo = user("g2", "Gerente Dos", STORE_A, Role.GERENTE);
        inactivo.setActivo(false);

        mockChain(List.of(activo, inactivo), List.of(activo),
                  List.of(task("g1", STORE_A, true)),
                  List.of(store(STORE_A, "Sucursal Centro")));

        List<LeaderboardEntryDTO> ranking = service.getGerencialesLeaderboard("weekly");

        assertEquals(1, ranking.size());
        assertEquals("g1", ranking.get(0).getUserId());
    }

    @Test
    void sinGerentesDevuelveVacioSinConsultarTareas() {
        when(userRepository.findByRolesContaining(Role.GERENTE)).thenReturn(List.of());

        assertTrue(service.getGerencialesLeaderboard("weekly").isEmpty());
        verify(taskRepository, never()).findByActivoTrue();
    }

    @Test
    void usaBatchFetchingSinNMasUnaConsultas() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User g2 = user("g2", "Gerente Dos", STORE_B, Role.GERENTE);
        List<User> equipo = List.of(
                ejecutador("e1", STORE_A, "g1"),
                ejecutador("e2", STORE_A, "g1"),
                ejecutador("e3", STORE_B, "g2"));

        mockChain(
                List.of(g1, g2),
                List.of(g1, g2, equipo.get(0), equipo.get(1), equipo.get(2)),
                List.of(task("g1", STORE_A, true), task("g2", STORE_B, true)),
                List.of(store(STORE_A, "Centro"), store(STORE_B, "Norte")));

        service.getGerencialesLeaderboard("weekly");

        // 5 gerentes/ejecutadores en juego, pero el número de queries no depende de N.
        verify(taskRepository, times(1)).findByActivoTrue();
        verify(userRepository, times(1)).findByActivoTrue();
        verify(storeRepository, times(1)).findAll();
        verify(taskRepository, never()).findByStoreIdAndActivoTrue(anyString());
        verify(taskRepository, never()).findByAssignedUserIdAndActivoTrue(anyString());
    }

    @Test
    void periodoMensualOtorgaColaboradorDelMesAlMejorEquipo() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User g2 = user("g2", "Gerente Dos", STORE_B, Role.GERENTE);
        User e1 = ejecutador("e1", STORE_A, "g1");   // 40.0
        User e2 = ejecutador("e2", STORE_B, "g2");   // 90.0

        mockChain(
                List.of(g1, g2),
                List.of(g1, g2, e1, e2),
                List.of(task("e1", STORE_A, false), task("e2", STORE_B, true)),
                List.of(store(STORE_A, "Centro"), store(STORE_B, "Norte")));

        List<LeaderboardEntryDTO> ranking = service.getGerencialesLeaderboard("monthly");

        assertEquals("g2", ranking.get(0).getUserId());
        assertTrue(hasBadge(ranking.get(0), "COLABORADOR_MES"),
                   "el top-1 mensual recibe la insignia");
        assertFalse(hasBadge(ranking.get(1), "COLABORADOR_MES"));
    }

    @Test
    void noOtorgaInsigniaSiNingunEquipoTieneDatos() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);

        // El gerente ejecuta bien en persona, pero no hay equipo que premiar.
        mockChain(List.of(g1), List.of(g1), List.of(task("g1", STORE_A, true)),
                  List.of(store(STORE_A, "Centro")));

        List<LeaderboardEntryDTO> ranking = service.getGerencialesLeaderboard("monthly");

        assertEquals(-1.0, ranking.get(0).getTeamAvgIgeo());
        assertFalse(hasBadge(ranking.get(0), "COLABORADOR_MES"));
    }

    @Test
    void periodoSemanalNoOtorgaColaboradorDelMes() {
        User g1 = user("g1", "Gerente Uno", STORE_A, Role.GERENTE);
        User e1 = ejecutador("e1", STORE_A, "g1");

        mockChain(List.of(g1), List.of(g1, e1), List.of(task("e1", STORE_A, true)),
                  List.of(store(STORE_A, "Centro")));

        List<LeaderboardEntryDTO> ranking = service.getGerencialesLeaderboard("weekly");

        assertEquals(90.0, ranking.get(0).getTeamAvgIgeo(), "hay equipo con datos");
        assertFalse(hasBadge(ranking.get(0), "COLABORADOR_MES"), "pero el período es semanal");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static LeaderboardEntryDTO byId(List<LeaderboardEntryDTO> ranking, String userId) {
        return ranking.stream()
                .filter(e -> userId.equals(e.getUserId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay entrada para " + userId));
    }

    private static boolean hasBadge(LeaderboardEntryDTO entry, String type) {
        return entry.getBadges().stream().anyMatch(b -> type.equals(b.getType()));
    }
}
