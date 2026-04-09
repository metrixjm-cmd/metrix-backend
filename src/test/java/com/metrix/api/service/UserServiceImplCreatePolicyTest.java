package com.metrix.api.service;

import com.metrix.api.dto.CreateUserRequest;
import com.metrix.api.dto.UserResponse;
import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplCreatePolicyTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SequenceService sequenceService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, passwordEncoder, sequenceService);
    }

    @Test
    void gerente_can_create_only_ejecutador_in_own_store() {
        when(userRepository.findByNumeroUsuario("GER001"))
                .thenReturn(Optional.of(user("GER001", "store-1", Set.of(Role.GERENTE))));
        when(sequenceService.generateUserFolio("EJECUTADOR", "Cajero")).thenReturn("CAJ001");
        when(userRepository.existsByNumeroUsuario("CAJ001")).thenReturn(false);
        when(passwordEncoder.encode("Operador123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u-new");
            return u;
        });

        CreateUserRequest req = createReq("store-1", Set.of(Role.EJECUTADOR));
        UserResponse created = service.createUser(req, "GER001");

        assertEquals("u-new", created.getId());
        assertTrue(created.getRoles().contains(Role.EJECUTADOR));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("store-1", captor.getValue().getStoreId());
    }

    @Test
    void gerente_forces_role_to_ejecutador_even_if_payload_sends_other_role() {
        when(userRepository.findByNumeroUsuario("GER001"))
                .thenReturn(Optional.of(user("GER001", "store-1", Set.of(Role.GERENTE))));
        when(sequenceService.generateUserFolio("EJECUTADOR", "Cajero")).thenReturn("CAJ001");
        when(userRepository.existsByNumeroUsuario("CAJ001")).thenReturn(false);
        when(passwordEncoder.encode("Operador123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u-gerente-role-normalized");
            return u;
        });

        CreateUserRequest req = createReq("store-1", Set.of(Role.GERENTE));
        UserResponse created = service.createUser(req, "GER001");

        assertEquals("u-gerente-role-normalized", created.getId());
        assertEquals(Set.of(Role.EJECUTADOR), created.getRoles());
    }

    @Test
    void gerente_forces_store_to_his_own_store_even_if_payload_sends_other_store() {
        when(userRepository.findByNumeroUsuario("GER001"))
                .thenReturn(Optional.of(user("GER001", "store-1", Set.of(Role.GERENTE))));
        when(sequenceService.generateUserFolio("EJECUTADOR", "Cajero")).thenReturn("CAJ002");
        when(userRepository.existsByNumeroUsuario("CAJ002")).thenReturn(false);
        when(passwordEncoder.encode("Operador123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u-gerente-store-normalized");
            return u;
        });

        CreateUserRequest req = createReq("store-2", Set.of(Role.EJECUTADOR));
        UserResponse created = service.createUser(req, "GER001");

        assertEquals("u-gerente-store-normalized", created.getId());
        assertEquals("store-1", created.getStoreId());
    }

    @Test
    void admin_can_create_gerente() {
        when(userRepository.findByNumeroUsuario("ADM001"))
                .thenReturn(Optional.of(user("ADM001", "store-1", Set.of(Role.ADMIN))));
        when(sequenceService.generateUserFolio("GERENTE", "Cajero")).thenReturn("GER999");
        when(userRepository.existsByNumeroUsuario("GER999")).thenReturn(false);
        when(passwordEncoder.encode("Operador123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u-admin-created");
            return u;
        });

        CreateUserRequest req = createReq("store-2", Set.of(Role.GERENTE));
        UserResponse created = service.createUser(req, "ADM001");

        assertEquals("u-admin-created", created.getId());
        assertTrue(created.getRoles().contains(Role.GERENTE));
    }

    private User user(String numeroUsuario, String storeId, Set<Role> roles) {
        return User.builder()
                .numeroUsuario(numeroUsuario)
                .storeId(storeId)
                .roles(roles)
                .activo(true)
                .build();
    }

    private CreateUserRequest createReq(String storeId, Set<Role> roles) {
        CreateUserRequest req = new CreateUserRequest();
        req.setNombre("Persona Demo");
        req.setPuesto("Cajero");
        req.setStoreId(storeId);
        req.setTurno("MATUTINO");
        req.setPassword("Operador123");
        req.setRoles(roles);
        return req;
    }
}
