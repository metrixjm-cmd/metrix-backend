package com.metrix.api.service;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.model.Role;
import com.metrix.api.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona las conexiones SSE activas y el envío de eventos en tiempo real.
 * <p>
 * Modo single-instance: entrega directa a emitters locales.
 * Con Cloud Run max-instances=1, no se necesita Redis Pub/Sub.
 * <p>
 * Si en el futuro se necesita multi-instancia, se puede re-activar
 * Redis Pub/Sub via {@code metrix.redis.enabled=true} y restaurar
 * {@code RedisConfig} + {@code RedisSSESubscriber}.
 */
@Slf4j
@Service
public class NotificationService {

    private final UserRepository userRepository;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public NotificationService(UserRepository userRepository) {
        this.userRepository = userRepository;
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

    // ── Envío directo (single-instance) ─────────────────────────────────────

    public void sendToUser(String userId, NotificationEvent event) {
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
                deliverToLocalEmitter(managerId, event);
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
                .map(u -> u.getId())
                .forEach(managerId -> deliverToLocalEmitter(managerId, event));
    }

    public void sendToAllAdmins(NotificationEvent event) {
        userRepository.findByRolesContaining(Role.ADMIN)
                .forEach(admin -> deliverToLocalEmitter(admin.getId(), event));
    }

    public int activeConnections() {
        return emitters.size();
    }

    // ── Internal ────────────────────────────────────────────────────────────

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
