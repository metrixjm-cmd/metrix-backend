package com.metrix.api.service;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.model.Role;
import com.metrix.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona las conexiones SSE activas y el envío de eventos en tiempo real (Sprint 6).
 * <p>
 * Principios de diseño:
 * <ul>
 *   <li>Un usuario → un emitter (la nueva conexión reemplaza la anterior).</li>
 *   <li>{@link ConcurrentHashMap} para acceso thread-safe desde múltiples requests.</li>
 *   <li>Errores de send limpian el emitter para evitar memory leaks.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserRepository userRepository;

    /** Mapa userId (MongoDB _id) → SseEmitter activo. */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // ── Suscripción ───────────────────────────────────────────────────────────

    /**
     * Registra un nuevo emitter SSE para el usuario.
     * Si ya existía una conexión activa para el mismo userId, la reemplaza.
     *
     * @param userId MongoDB _id del usuario autenticado
     * @return SseEmitter configurado con timeout infinito
     */
    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> {
            emitters.remove(userId, emitter);
            log.debug("SSE completado para usuario: {}", userId);
        });

        emitter.onTimeout(() -> {
            emitters.remove(userId, emitter);
            emitter.complete();
            log.debug("SSE timeout para usuario: {}", userId);
        });

        emitter.onError(e -> {
            emitters.remove(userId, emitter);
            log.debug("SSE error para usuario {}: {}", userId, e.getMessage());
        });

        emitters.put(userId, emitter);
        log.info("SSE conectado — userId: {} | conexiones activas: {}", userId, emitters.size());

        // Envía evento inicial de confirmación de conexión
        try {
            emitter.send(SseEmitter.event().name("connected").data("OK"));
        } catch (IOException e) {
            emitters.remove(userId, emitter);
        }

        return emitter;
    }

    // ── Envío de eventos ─────────────────────────────────────────────────────

    /**
     * Envía una notificación a un usuario específico.
     * No hace nada si el usuario no tiene una conexión SSE activa.
     *
     * @param userId MongoDB _id del destinatario
     * @param event  payload del evento
     */
    public void sendToUser(String userId, NotificationEvent event) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .id(event.getId())
                    .name("notification")
                    .data(event));
            log.debug("Notificación enviada a {}: {}", userId, event.getType());
        } catch (IOException e) {
            emitters.remove(userId, emitter);
            log.warn("Error enviando notificación a {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Envía una notificación a todos los GERENTEs y ADMINs activos de una sucursal.
     * Útil para eventos operativos que el supervisor debe conocer.
     *
     * @param storeId ID de la sucursal
     * @param event   payload del evento
     */
    public void sendToStoreManagers(String storeId, NotificationEvent event) {
        userRepository.findByStoreIdAndActivoTrue(storeId).stream()
                .filter(u -> u.getRoles().contains(Role.GERENTE) || u.getRoles().contains(Role.ADMIN))
                .map(u -> u.getId())
                .forEach(managerId -> sendToUser(managerId, event));
    }

    /**
     * Envía una notificación a todos los usuarios con rol ADMIN que tengan una conexión SSE activa.
     * Usado por el {@code AlertScheduler} para alertas diarias de IGEO (Sprint 16).
     *
     * @param event payload del evento
     */
    public void sendToAllAdmins(NotificationEvent event) {
        userRepository.findByRolesContaining(Role.ADMIN)
                .forEach(admin -> sendToUser(admin.getId(), event));
    }

    /** Retorna el número de conexiones SSE activas en este momento. */
    public int activeConnections() {
        return emitters.size();
    }
}
