package com.metrix.api.service;

import com.metrix.api.dto.AuthRequest;
import com.metrix.api.dto.AuthResponse;
import com.metrix.api.exception.TooManyLoginAttemptsException;
import com.metrix.api.model.User;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.security.JwtService;
import com.metrix.api.security.LoginAttemptLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Servicio de autenticación.
 * <p>
 * El alta de usuarios vive en {@code UserServiceImpl.createUser}, que aplica la
 * política de roles. Aquí había un {@code register} que tomaba los roles del cuerpo
 * de la petición y estaba expuesto sin autenticación; se eliminó con el endpoint.
 * <p>
 * Principios SOLID aplicados:
 * - SRP: Solo gestiona flujos de autenticación.
 * - DIP: Depende de abstracciones (UserRepository, PasswordEncoder, JwtService).
 * <p>
 * El token incluye claims extra (roles, storeId, turno) para que Angular
 * pueda renderizar la UI según el perfil sin un GET /me adicional.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptLimiter loginAttemptLimiter;

    /**
     * Autentica un usuario existente y devuelve un JWT.
     * <p>
     * Los fallos se cuentan por cuenta ({@link LoginAttemptLimiter}) además del
     * límite por cliente del {@code RateLimitFilter}: repartir los intentos entre
     * varias IPs no debe servir para adivinar una contraseña.
     */
    public AuthResponse login(AuthRequest request) {
        String numeroUsuario = request.getNumeroUsuario();

        if (loginAttemptLimiter.isBlocked(numeroUsuario)) {
            throw new TooManyLoginAttemptsException(
                    "Demasiados intentos fallidos. Vuelve a intentarlo en unos minutos.");
        }

        try {
            // Spring Security valida credenciales y lanza AuthenticationException si fallan
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            numeroUsuario,
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException e) {
            loginAttemptLimiter.recordFailure(numeroUsuario);
            throw e;
        }

        loginAttemptLimiter.recordSuccess(numeroUsuario);

        User user = userRepository.findByNumeroUsuario(numeroUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        return buildAuthResponse(user);
    }

    // ── Helper ─────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        // Resolver nombre de sucursal para evitar que el frontend necesite GET /stores
        String storeName = "";
        if (user.getStoreId() != null && !user.getStoreId().isBlank()) {
            storeName = storeRepository.findById(user.getStoreId())
                    .map(s -> s.getNombre() != null ? s.getNombre() : s.getCodigo())
                    .orElse("");
        }

        // Claims extra en el JWT para que Angular tenga contexto inmediato
        Map<String, Object> extraClaims = new java.util.HashMap<>();
        extraClaims.put("roles", user.getRoles());
        extraClaims.put("storeId", user.getStoreId() != null ? user.getStoreId() : "");
        extraClaims.put("turno", user.getTurno() != null ? user.getTurno() : "");
        extraClaims.put("nombre", user.getNombre() != null ? user.getNombre() : "");

        org.springframework.security.core.userdetails.User userDetails =
                new org.springframework.security.core.userdetails.User(
                        user.getNumeroUsuario(),
                        user.getPassword(),
                        java.util.Collections.emptyList()
                );

        String token = jwtService.generateToken(extraClaims, userDetails);

        return AuthResponse.builder()
                .token(token)
                .numeroUsuario(user.getNumeroUsuario())
                .nombre(user.getNombre())
                .storeId(user.getStoreId())
                .storeName(storeName)
                .turno(user.getTurno())
                .roles(user.getRoles())
                .build();
    }
}
