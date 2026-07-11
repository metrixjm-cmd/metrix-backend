package com.metrix.api.service;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.dto.NotificationResponse;
import com.metrix.api.model.Notification;
import com.metrix.api.model.Role;
import com.metrix.api.repository.NotificationRepository;
import com.metrix.api.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona las conexiones SSE activas y el envío/persistencia de notificaciones.
 * <p>
 * Cada notificación se persiste en Mongo (colección {@code notifications})
 * además de intentar la entrega en vivo por SSE — así el usuario ve su
 * historial al iniciar sesión aunque no haya estado conectado en el
 * momento del evento (antes se perdía para siempre).
 * <p>
 * Modo single-instance: entrega directa a emitters locales.
 * Con Cloud Run max-instances=1, no se necesita Redis Pub/Sub.
 */
@Slf4j
@Service
public class NotificationService {

    private final UserRepository         userRepository;
    private final NotificationRepository notificationRepository;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public NotificationService(UserRepository userRepository,
                                NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    // ── Suscripción ─────────────────────────────────────────────────────────

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        emitter.onCompletion(() -> {
            emitters.remove(userId, emitter);
            log.debug("SSE completado para usuario: {}", userId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(userId, emitter);
            log.debug("SSE timeout para usuario: {}", userId);
        });
        emitter.onError(e -> {
            emitters.remove(userId, emitter);
            log.debug("SSE error para usuario {}: {}", userId, e.getMessage());
        });

        // Cerrar emitter anterior del mismo usuario
        SseEmitter old = emitters.put(userId, emitter);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) {}
        }
        log.info("SSE conectado — userId: {} | conexiones activas: {}", userId, emitters.size());

        try {
            emitter.send(SseEmitter.event().name("connected").data("OK"));
        } catch (IOException e) {
            emitters.remove(userId, emitter);
        }
        return emitter;
    }

    // ── Envío (persiste + intenta entrega en vivo) ───────────────────────────

    public void sendToUser(String userId, NotificationEvent event) {
        if (userId == null || userId.isBlank()) return;
        persist(userId, event);
        deliverToLocalEmitter(userId, event);
    }

    /**
     * Notifica al gerente responsable del colaborador ({@code managerOwnerId}).
     * Evita que todos los gerentes de la sucursal reciban alertas de equipos ajenos.
     */
    public void sendToManagerOfAssignee(String assigneeUserId, NotificationEvent event) {
        if (assigneeUserId == null || assigneeUserId.isBlank()) {
            log.debug("Omitiendo notificación a gerente: assigneeUserId vacío ({})", event.getType());
            return;
        }

        userRepository.findById(assigneeUserId).ifPresentOrElse(assignee -> {
            String managerId = assignee.getManagerOwnerId();
            if (managerId != null && !managerId.isBlank()) {
                sendToUser(managerId, event);
                return;
            }
            if (assignee.getRoles() != null && assignee.getRoles().contains(Role.GERENTE)) {
                sendToAllAdmins(event);
                return;
            }
            log.warn("Colaborador {} sin managerOwnerId — alerta {} no enviada a gerente",
                    assigneeUserId, event.getType());
        }, () -> log.warn("Assignee {} no encontrado — alerta {} omitida", assigneeUserId, event.getType()));
    }

    /** @deprecated Preferir {@link #sendToManagerOfAssignee(String, NotificationEvent)}. */
    @Deprecated
    public void sendToStoreManagers(String storeId, NotificationEvent event) {
        userRepository.findByStoreIdAndActivoTrue(storeId).stream()
                .filter(u -> u.getRoles().contains(Role.GERENTE) || u.getRoles().contains(Role.ADMIN))
                .forEach(u -> sendToUser(u.getId(), event));
    }

    public void sendToAllAdmins(NotificationEvent event) {
        userRepository.findByRolesContaining(Role.ADMIN)
                .forEach(admin -> sendToUser(admin.getId(), event));
    }

    public int activeConnections() {
        return emitters.size();
    }

    // ── Historial persistido ──────────────────────────────────────────────

    /** Últimas 50 notificaciones del usuario, más recientes primero. */
    public List<NotificationResponse> getRecent(String userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Marca una notificación como leída. No-op si no pertenece al usuario. */
    public void markRead(String userId, String notificationId) {
        notificationRepository.findById(notificationId)
                .filter(n -> userId.equals(n.getUserId()))
                .ifPresent(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                });
    }

    /** Marca todas las notificaciones no leídas del usuario como leídas. */
    public void markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private void persist(String userId, NotificationEvent event) {
        // Reutiliza el mismo id del evento SSE: así markRead(id) funciona
        // igual sea que el cliente haya recibido la notificación en vivo o
        // via GET /notifications, y el merge cliente-side puede deduplicar.
        notificationRepository.save(Notification.builder()
                .id(event.getId())
                .userId(userId)
                .type(event.getType())
                .severity(event.getSeverity())
                .title(event.getTitle())
                .body(event.getBody())
                .taskId(event.getTaskId())
                .incidentId(event.getIncidentId())
                .examId(event.getExamId())
                .storeId(event.getStoreId())
                .read(false)
                .createdAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .build());
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .severity(n.getSeverity())
                .title(n.getTitle())
                .body(n.getBody())
                .taskId(n.getTaskId())
                .incidentId(n.getIncidentId())
                .examId(n.getExamId())
                .storeId(n.getStoreId())
                .read(n.isRead())
                .timestamp(n.getCreatedAt())
                .build();
    }

    private void deliverToLocalEmitter(String userId, NotificationEvent event) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .id(event.getId())
                    .name("notification")
                    .data(event));
            log.debug("Notificación entregada a {}: {}", userId, event.getType());
        } catch (IOException e) {
            emitters.remove(userId, emitter);
            log.warn("Error entregando notificación a {}: {}", userId, e.getMessage());
        }
    }
}
