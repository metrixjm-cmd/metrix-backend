package com.metrix.api.service;

import com.metrix.api.dto.AuthRequest;
import com.metrix.api.dto.AuthResponse;
import com.metrix.api.dto.RegisterRequest;
import com.metrix.api.model.User;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Servicio de autenticación y registro de usuarios.
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
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Registra un nuevo usuario en el sistema.
     *
     * @throws IllegalArgumentException si el #Usuario ya existe.
     */
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByNumeroUsuario(request.getNumeroUsuario())) {
            throw new IllegalArgumentException(
                    "El número de usuario ya está registrado: " + request.getNumeroUsuario()
            );
        }

        User user = User.builder()
                .nombre(request.getNombre())
                .puesto(request.getPuesto())
                .storeId(request.getStoreId())
                .turno(request.getTurno())
                .numeroUsuario(request.getNumeroUsuario())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(request.getRoles())
                .activo(true)
                .build();

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    /**
     * Autentica un usuario existente y devuelve un JWT.
     */
    public AuthResponse login(AuthRequest request) {
        // Spring Security valida credenciales y lanza AuthenticationException si fallan
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getNumeroUsuario(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByNumeroUsuario(request.getNumeroUsuario())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        return buildAuthResponse(user);
    }

    // ── Helper ─────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        // Claims extra en el JWT para que Angular tenga contexto inmediato
        Map<String, Object> extraClaims = Map.of(
                "roles", user.getRoles(),
                "storeId", user.getStoreId(),
                "turno", user.getTurno(),
                "nombre", user.getNombre()
        );

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
                .turno(user.getTurno())
                .roles(user.getRoles())
                .build();
    }
}
