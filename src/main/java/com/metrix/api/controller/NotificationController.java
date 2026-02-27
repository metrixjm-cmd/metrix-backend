package com.metrix.api.controller;

import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.security.JwtService;
import com.metrix.api.security.UserDetailsServiceImpl;
import com.metrix.api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller para el stream SSE de notificaciones en tiempo real (Sprint 6).
 * <p>
 * Base path: {@code /api/v1/notifications}
 * <p>
 * El endpoint SSE acepta el JWT como query param porque el browser nativo
 * {@code EventSource} no soporta headers personalizados. El token se valida
 * manualmente antes de registrar el emitter.
 * <p>
 * Este endpoint está marcado como {@code permitAll()} en SecurityConfig para
 * que Spring Security no lo intercepte; la autenticación se realiza aquí mismo.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService    notificationService;
    private final JwtService             jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserRepository         userRepository;

    /**
     * GET /api/v1/notifications/stream?token={jwt}
     * <p>
     * Establece una conexión SSE persistente para el usuario autenticado.
     * Emite eventos de tipo {@code notification} con payload JSON cuando
     * ocurren eventos operativos relevantes para el usuario.
     * <p>
     * Ejemplo de evento recibido:
     * <pre>
     * event: notification
     * id: uuid
     * data: {"type":"TASK_FAILED","severity":"critical","title":"...","body":"..."}
     * </pre>
     *
     * @param token JWT del usuario (pasado como query param por limitación del EventSource API)
     * @return SseEmitter con stream de eventos
     * @throws IllegalArgumentException si el token es inválido o expirado
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("token") String token) {
        // 1. Extraer y validar el JWT manualmente
        String numeroUsuario;
        try {
            numeroUsuario = jwtService.extractUsername(token);
        } catch (Exception e) {
            throw new IllegalArgumentException("Token de autenticación inválido o malformado.");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(numeroUsuario);
        if (!jwtService.isTokenValid(token, userDetails)) {
            throw new IllegalArgumentException("Token de autenticación expirado o inválido.");
        }

        // 2. Resolver el MongoDB _id del usuario
        String userId = userRepository.findByNumeroUsuario(numeroUsuario)
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado: " + numeroUsuario));

        // 3. Registrar y retornar el emitter SSE
        return notificationService.subscribe(userId);
    }
}
