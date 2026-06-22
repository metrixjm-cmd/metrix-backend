package com.metrix.api.service;

import com.metrix.api.model.ExamAudience;
import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para RolePolicy.
 * Cubren reglas de asignación y validación de scope de operación.
 */
class RolePolicyTest {

    private RolePolicy rolePolicy;

    @BeforeEach
    void setUp() {
        rolePolicy = new RolePolicy();
    }

    // ── validateAssignment ──────────────────────────────────────────────

    @Test
    void admin_canAssign_toGerente() {
        User admin = buildUser("admin1", Role.ADMIN, "store-a");
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");

        assertDoesNotThrow(() -> rolePolicy.validateAssignment(admin, gerente, "store-a"));
    }

    @Test
    void admin_canAssign_toEjecutador_forGenericTraining() {
        // La política GENÉRICA de capacitaciones permite ADMIN → GERENTE o EJECUTADOR.
        // La restricción "ADMIN solo a gerentes" aplica únicamente a exámenes
        // (ver validateExamAssignment).
        User admin = buildUser("admin1", Role.ADMIN, "store-a");
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-a");

        assertDoesNotThrow(() -> rolePolicy.validateAssignment(admin, ejecutador, "store-a"));
    }

    @Test
    void gerente_canAssign_toEjecutadorInSameStore() {
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-a");

        assertDoesNotThrow(() -> rolePolicy.validateAssignment(gerente, ejecutador, "store-a"));
    }

    @Test
    void gerente_cannotAssign_toEjecutadorInDifferentStore() {
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-b");

        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateAssignment(gerente, ejecutador, "store-b"));
    }

    @Test
    void gerente_cannotAssign_toGerente() {
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");
        User otroGerente = buildUser("ger2", Role.GERENTE, "store-a");

        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateAssignment(gerente, otroGerente, "store-a"));
    }

    @Test
    void ejecutador_cannotAssign_toAnyone() {
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-a");
        User otroEjecutador = buildUser("eje2", Role.EJECUTADOR, "store-a");

        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateAssignment(ejecutador, otroEjecutador, "store-a"));
    }

    @Test
    void assignee_mustBelongToStore() {
        User admin = buildUser("admin1", Role.ADMIN, "store-a");
        User gerente = buildUser("ger1", Role.GERENTE, "store-b");

        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateAssignment(admin, gerente, "store-a"));
    }

    // ── validateExamAssignment (delegación en dos niveles) ──────────────

    @Test
    void examAssign_admin_canAssign_toGerente_regardlessOfAudience() {
        User admin = buildUser("admin1", Role.ADMIN, "store-a");
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");

        assertDoesNotThrow(() ->
                rolePolicy.validateExamAssignment(admin, gerente, ExamAudience.EJECUTADOR));
        assertDoesNotThrow(() ->
                rolePolicy.validateExamAssignment(admin, gerente, ExamAudience.GERENTE));
    }

    @Test
    void examAssign_admin_cannotAssign_toEjecutador() {
        User admin = buildUser("admin1", Role.ADMIN, "store-a");
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-a");

        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateExamAssignment(admin, ejecutador, ExamAudience.EJECUTADOR));
    }

    @Test
    void examAssign_gerente_canRedistribute_executorExam_toEjecutador() {
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-a");

        assertDoesNotThrow(() ->
                rolePolicy.validateExamAssignment(gerente, ejecutador, ExamAudience.EJECUTADOR));
    }

    @Test
    void examAssign_gerente_cannotRedistribute_managerExam() {
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-a");

        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateExamAssignment(gerente, ejecutador, ExamAudience.GERENTE));
    }

    @Test
    void examAssign_gerente_cannotAssign_toGerente() {
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");
        User otroGerente = buildUser("ger2", Role.GERENTE, "store-a");

        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateExamAssignment(gerente, otroGerente, ExamAudience.EJECUTADOR));
    }

    @Test
    void examAssign_nullAudience_treatedAsEjecutador() {
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-a");

        assertDoesNotThrow(() ->
                rolePolicy.validateExamAssignment(gerente, ejecutador, null));
    }

    @Test
    void examAssign_ejecutador_cannotAssign() {
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-a");
        User otro = buildUser("eje2", Role.EJECUTADOR, "store-a");

        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateExamAssignment(ejecutador, otro, ExamAudience.EJECUTADOR));
    }

    // ── validateOperationScope ──────────────────────────────────────────

    @Test
    void admin_canOperate_onAnyTraining() {
        User admin = buildUser("admin1", Role.ADMIN, "store-a");

        assertDoesNotThrow(() ->
                rolePolicy.validateOperationScope(admin, "any-user-id", "any-store"));
    }

    @Test
    void gerente_canOperate_onTrainingInHisStore() {
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");

        assertDoesNotThrow(() ->
                rolePolicy.validateOperationScope(gerente, "some-eje-id", "store-a"));
    }

    @Test
    void gerente_cannotOperate_onTrainingOutsideHisStore() {
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");

        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateOperationScope(gerente, "some-eje-id", "store-b"));
    }

    @Test
    void ejecutador_canOperate_onOwnTraining() {
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-a");

        assertDoesNotThrow(() ->
                rolePolicy.validateOperationScope(ejecutador, "eje1", "store-a"));
    }

    @Test
    void ejecutador_cannotOperate_onOthersTraining() {
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-a");

        assertThrows(IllegalStateException.class,
                () -> rolePolicy.validateOperationScope(ejecutador, "eje2", "store-a"));
    }

    // ── targetRoleFor ───────────────────────────────────────────────────

    @Test
    void admin_targetsGerente() {
        User admin = buildUser("admin1", Role.ADMIN, "store-a");
        assertEquals(Role.GERENTE, rolePolicy.targetRoleFor(admin));
    }

    @Test
    void gerente_targetsEjecutador() {
        User gerente = buildUser("ger1", Role.GERENTE, "store-a");
        assertEquals(Role.EJECUTADOR, rolePolicy.targetRoleFor(gerente));
    }

    @Test
    void ejecutador_targetsNull() {
        User ejecutador = buildUser("eje1", Role.EJECUTADOR, "store-a");
        assertNull(rolePolicy.targetRoleFor(ejecutador));
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private User buildUser(String id, Role role, String storeId) {
        return User.builder()
                .id(id)
                .nombre("Test " + id)
                .roles(Set.of(role))
                .storeId(storeId)
                .activo(true)
                .build();
    }
}
