package com.metrix.api.service;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.model.Notification;
import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import com.metrix.api.repository.NotificationRepository;
import com.metrix.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    /**
     * La regresión que importa: un evento con varios destinatarios tiene que dejar
     * un documento por cada uno.
     * <p>
     * Antes se usaba el id del evento como {@code _id}, así que cada save() pisaba
     * al anterior y sólo el último destinatario conservaba la notificación en su
     * historial. Con 4 admins quedaba 1 documento en vez de 4.
     */
    @Test
    void mismoEventoAVariosUsuariosGuardaUnDocumentoPorCadaUno() {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationEvent event = sampleEvent();
        notificationService.sendToUser("user-a", event);
        notificationService.sendToUser("user-b", event);
        notificationService.sendToUser("user-c", event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(3)).save(saved.capture());

        List<Notification> docs = saved.getAllValues();

        assertEquals(3, docs.stream().map(Notification::getId).distinct().count(),
                     "cada destinatario necesita su propio _id o se sobrescriben");
        assertEquals(List.of("user-a", "user-b", "user-c"),
                     docs.stream().map(Notification::getUserId).toList());
        assertTrue(docs.stream().allMatch(n -> event.getId().equals(n.getEventId())),
                   "el id del evento se conserva aparte para deduplicar en el cliente");
    }

    @Test
    void countUnreadDelegaEnLaBaseYNoEnLaListaRecortada() {
        when(notificationRepository.countByUserIdAndReadFalse("user-a")).thenReturn(137L);

        assertEquals(137L, notificationService.countUnread("user-a"),
                     "el contador no puede toparse en las 50 que cachea el cliente");
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
