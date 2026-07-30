package com.metrix.api.service;

import com.metrix.api.dto.LeaderboardEntryDTO;
import com.metrix.api.model.Execution;
import com.metrix.api.model.Role;
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
 * Ranking de equipo del GERENTE — {@code getTeamLeaderboard}.
 * <p>
 * Una sucursal puede tener varios gerentes. Cada uno debe ver sólo a los
 * ejecutadores con su {@code managerOwnerId}, nunca los del gerente de al lado
 * ni a otros gerentes.
 */
@ExtendWith(MockitoExtension.class)
class GamificationTeamLeaderboardTest {

    private static final String STORE     = "store-1";
    private static final String GERENTE_A = "ger-a";
    private static final String GERENTE_B = "ger-b";

    @Mock private TaskRepository           taskRepository;
    @Mock private UserRepository           userRepository;
    @Mock private ExamSubmissionRepository submissionRepository;
    @Mock private StoreRepository          storeRepository;

    @InjectMocks private GamificationServiceImpl service;

    // ── Fixtures ──────────────────────────────────────────────────────────

    private static User ejecutador(String id, String managerOwnerId) {
        User u = User.builder()
                .id(id)
                .nombre("Ejecutador " + id)
                .numeroUsuario("USR-" + id)
                .storeId(STORE)
                .turno("MATUTINO")
                .roles(Set.of(Role.EJECUTADOR))
                .activo(true)
                .build();
        u.setManagerOwnerId(managerOwnerId);
        return u;
    }

    private static User gerente(String id) {
        User u = User.builder()
                .id(id)
                .nombre("Gerente " + id)
                .numeroUsuario("USR-" + id)
                .storeId(STORE)
                .turno("MATUTINO")
                .roles(Set.of(Role.GERENTE))
                .activo(true)
                .build();
        u.setManagerOwnerId(id);
        return u;
    }

    private static Task task(String userId, boolean onTime) {
        Instant cuando = Instant.now().minus(1, ChronoUnit.DAYS);
        return Task.builder()
                .id("task-" + userId + "-" + onTime)
                .assignedUserId(userId)
                .storeId(STORE)
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

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    void soloRankeaLosEjecutadoresACargoDelGerente() {
        User mio   = ejecutador("eje-1", GERENTE_A);
        User ajeno = ejecutador("eje-2", GERENTE_B);

        // El repositorio ya filtra por managerOwnerId: sólo devuelve los del gerente A.
        when(userRepository.findByStoreIdAndManagerOwnerIdAndActivoTrue(STORE, GERENTE_A))
                .thenReturn(List.of(mio));
        when(taskRepository.findByStoreIdAndActivoTrue(STORE))
                .thenReturn(List.of(task("eje-1", true), task("eje-2", true)));

        List<LeaderboardEntryDTO> board = service.getTeamLeaderboard(STORE, GERENTE_A, "weekly");

        assertEquals(1, board.size());
        assertEquals("eje-1", board.get(0).getUserId());
        assertTrue(board.stream().noneMatch(e -> e.getUserId().equals(ajeno.getId())),
                   "El ranking no debe incluir ejecutadores de otro gerente");
    }

    @Test
    void excluyeAlPropioGerenteYAOtrosNoEjecutadores() {
        // El gerente se apunta a sí mismo como owner; el ranking es de su plantilla,
        // así que no debe aparecer compitiendo contra su propia gente.
        when(userRepository.findByStoreIdAndManagerOwnerIdAndActivoTrue(STORE, GERENTE_A))
                .thenReturn(List.of(gerente(GERENTE_A), ejecutador("eje-1", GERENTE_A)));
        when(taskRepository.findByStoreIdAndActivoTrue(STORE))
                .thenReturn(List.of(task("eje-1", true)));

        List<LeaderboardEntryDTO> board = service.getTeamLeaderboard(STORE, GERENTE_A, "weekly");

        assertEquals(1, board.size());
        assertEquals("eje-1", board.get(0).getUserId());
    }

    @Test
    void numeraDesde1SinHuecos() {
        when(userRepository.findByStoreIdAndManagerOwnerIdAndActivoTrue(STORE, GERENTE_A))
                .thenReturn(List.of(ejecutador("eje-1", GERENTE_A),
                                    ejecutador("eje-2", GERENTE_A),
                                    ejecutador("eje-3", GERENTE_A)));
        when(taskRepository.findByStoreIdAndActivoTrue(STORE))
                .thenReturn(List.of(task("eje-1", true), task("eje-2", false), task("eje-3", true)));

        List<LeaderboardEntryDTO> board = service.getTeamLeaderboard(STORE, GERENTE_A, "weekly");

        assertEquals(List.of(1, 2, 3), board.stream().map(LeaderboardEntryDTO::getRank).toList());
    }

    @Test
    void ordenaPorOverAllDescendente() {
        when(userRepository.findByStoreIdAndManagerOwnerIdAndActivoTrue(STORE, GERENTE_A))
                .thenReturn(List.of(ejecutador("tarde", GERENTE_A), ejecutador("puntual", GERENTE_A)));
        when(taskRepository.findByStoreIdAndActivoTrue(STORE))
                .thenReturn(List.of(task("tarde", false), task("puntual", true)));

        List<LeaderboardEntryDTO> board = service.getTeamLeaderboard(STORE, GERENTE_A, "weekly");

        assertEquals("puntual", board.get(0).getUserId());
        assertTrue(board.get(0).getIgeo() > board.get(1).getIgeo());
    }

    @Test
    void devuelveVacioCuandoElGerenteNoTieneEquipo() {
        when(userRepository.findByStoreIdAndManagerOwnerIdAndActivoTrue(STORE, GERENTE_A))
                .thenReturn(List.of());
        when(taskRepository.findByStoreIdAndActivoTrue(STORE)).thenReturn(List.of());

        assertTrue(service.getTeamLeaderboard(STORE, GERENTE_A, "weekly").isEmpty());
    }
}
