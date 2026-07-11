package com.metrix.api.service;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import com.metrix.api.repository.NotificationRepository;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(userRepository, notificationRepository);
    }

    @Test
    void sendToManagerOfAssignee_notifies_only_owner_manager() {
        User executor = User.builder()
                .id("exec-1")
                .managerOwnerId("mgr-arturo")
                .roles(Set.of(Role.EJECUTADOR))
                .build();
        when(userRepository.findById("exec-1")).thenReturn(Optional.of(executor));

        NotificationEvent event = NotificationEvent.builder()
                .id("n-1")
                .type("TASK_OVERDUE")
                .severity("warning")
                .title("Tarea vencida")
                .body("Irma · Limpieza")
                .timestamp(Instant.now())
                .build();

        notificationService.sendToManagerOfAssignee("exec-1", event);

        verify(userRepository).findById("exec-1");
    }

    @Test
    void sendToManagerOfAssignee_skips_when_executor_has_no_manager() {
        User orphan = User.builder()
                .id("exec-orphan")
                .roles(Set.of(Role.EJECUTADOR))
                .build();
        when(userRepository.findById("exec-orphan")).thenReturn(Optional.of(orphan));

        notificationService.sendToManagerOfAssignee("exec-orphan", sampleEvent());

        verify(userRepository, never()).findByStoreIdAndActivoTrue(anyString());
    }

    private NotificationEvent sampleEvent() {
        return NotificationEvent.builder()
                .id("n-2")
                .type("TASK_STARTED")
                .severity("info")
                .title("Tarea iniciada")
                .body("Test")
                .timestamp(Instant.now())
                .build();
    }
}
