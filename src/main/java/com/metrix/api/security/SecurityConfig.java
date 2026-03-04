package com.metrix.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración de seguridad para METRIX.
 * <p>
 * Principios aplicados:
 * - Stateless (JWT) → No sesiones en servidor → escalabilidad horizontal.
 * - CORS habilitado para Angular en desarrollo (localhost:4200).
 * - CSRF deshabilitado → innecesario con JWT + stateless.
 * - @EnableMethodSecurity → permite @PreAuthorize a nivel de método
 *   para control granular por rol (ADMIN, GERENTE, EJECUTADOR).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // ── Preflight OPTIONS ──────────────────────────────
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ── Rutas públicas ─────────────────────────────────
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // ── SSE: token viaja como query param, validación manual en el controller ──
                .requestMatchers("/api/v1/notifications/stream").permitAll()

                // ── Rutas protegidas por rol ───────────────────────
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/gerente/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers("/api/v1/reports/**").hasAnyRole("ADMIN", "GERENTE")

                // ── Módulo RH (Sprint 9) ────────────────────────────
                .requestMatchers(HttpMethod.GET,   "/api/v1/users/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.POST,  "/api/v1/users").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,   "/api/v1/users/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/users/**").hasRole("ADMIN")

                // ── Módulo Capacitación (Sprint 10) ──────────────────
                .requestMatchers(HttpMethod.GET,    "/api/v1/trainings/store/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.POST,   "/api/v1/trainings").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/trainings/**").hasRole("ADMIN")

                // ── Módulo Configuración / Stores (Sprint 11) ────────
                .requestMatchers(HttpMethod.GET,   "/api/v1/stores/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.POST,  "/api/v1/stores").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,   "/api/v1/stores/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/stores/**").hasRole("ADMIN")

                // ── Módulo Gamificación (Sprint 12) ──────────────────
                .requestMatchers(HttpMethod.GET, "/api/v1/gamification/store/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.GET, "/api/v1/gamification/me").authenticated()

                // ── Módulo Contingencias (Sprint 15) ──────────────────
                .requestMatchers(HttpMethod.POST,  "/api/v1/incidents").authenticated()
                .requestMatchers(HttpMethod.GET,   "/api/v1/incidents/my").authenticated()
                .requestMatchers(HttpMethod.GET,   "/api/v1/incidents/store/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/incidents/**").hasAnyRole("ADMIN", "GERENTE")

                // ── Todo lo demás requiere autenticación ───────────
                .anyRequest().authenticated()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── Beans de Autenticación ─────────────────────────────────────────

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ── CORS para Angular (Multi-Dispositivo, Obj. #18) ────────────────

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",   // Angular dev
                "http://localhost:8080"    // Backend local
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
