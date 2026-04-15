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

import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
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

    @Value("${metrix.cors.allowed-origins}")
    private String allowedOriginsRaw;

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
                .requestMatchers("/api/v1/evidence/local/**").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // ── SSE: token viaja como query param, validación manual en el controller ──
                .requestMatchers("/api/v1/notifications/stream").permitAll()

                // ── Rutas protegidas por rol ───────────────────────
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/gerente/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers("/api/v1/reports/**").hasAnyRole("ADMIN", "GERENTE")

                // ── Módulo RH (Sprint 9) ────────────────────────────
                .requestMatchers(HttpMethod.GET,    "/api/v1/users/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.POST,   "/api/v1/users").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.PUT,    "/api/v1/users/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.PATCH,  "/api/v1/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**").hasAnyRole("ADMIN", "GERENTE")

                // ── Módulo Capacitación — autorización delegada a @PreAuthorize en controller ──
                .requestMatchers("/api/v1/trainings/**").authenticated()

                // ── Módulo Configuración / Stores (Sprint 11) ────────
                .requestMatchers(HttpMethod.GET,   "/api/v1/stores/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.POST,  "/api/v1/stores").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,   "/api/v1/stores/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/stores/**").hasRole("ADMIN")

                // ── Catálogos dinámicos ─────────────────────────────
                .requestMatchers(HttpMethod.GET,    "/api/v1/catalogs/**").authenticated()
                .requestMatchers(HttpMethod.POST,   "/api/v1/catalogs/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.PUT,    "/api/v1/catalogs/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/catalogs/**").hasAnyRole("ADMIN", "GERENTE")

                // ── Módulo Gamificación (Sprint 12) ──────────────────
                .requestMatchers(HttpMethod.GET, "/api/v1/gamification/store/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.GET, "/api/v1/gamification/me").authenticated()

                // ── Módulo Contingencias (Sprint 15) ──────────────────
                .requestMatchers(HttpMethod.POST,  "/api/v1/incidents").authenticated()
                .requestMatchers(HttpMethod.POST,  "/api/v1/incidents/*/evidence").authenticated()
                .requestMatchers(HttpMethod.GET,   "/api/v1/incidents/visible").authenticated()
                .requestMatchers(HttpMethod.GET,   "/api/v1/incidents/my").authenticated()
                .requestMatchers(HttpMethod.GET,   "/api/v1/incidents/store/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/incidents/**").hasAnyRole("ADMIN", "GERENTE")

                // ── Calificación de Calidad (Sprint 18) ───────────────
                .requestMatchers(HttpMethod.PATCH, "/api/v1/tasks/*/quality").hasAnyRole("ADMIN", "GERENTE")

                // ── Banco de Preguntas (E3) ────────────────────────────
                .requestMatchers(HttpMethod.GET,    "/api/v1/question-bank/**").authenticated()
                .requestMatchers(HttpMethod.POST,   "/api/v1/question-bank/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.PUT,    "/api/v1/question-bank/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/question-bank/**").hasRole("ADMIN")

                // ── Banco de Materiales + Plantillas ──────────────────
                .requestMatchers(HttpMethod.GET,    "/api/v1/training-materials/**").authenticated()
                .requestMatchers(HttpMethod.POST,   "/api/v1/training-materials/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/training-materials/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.GET,    "/api/v1/training-templates/**").authenticated()
                .requestMatchers(HttpMethod.POST,   "/api/v1/training-templates/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.PUT,    "/api/v1/training-templates/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/training-templates/**").hasRole("ADMIN")

                // ── Plantillas de Tarea (Banco de Tareas) ─────────────
                .requestMatchers(HttpMethod.GET,    "/api/v1/categorias", "/api/v1/categorias/**").authenticated()
                .requestMatchers(HttpMethod.POST,   "/api/v1/categorias", "/api/v1/categorias/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.PUT,    "/api/v1/categorias/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/categorias/**").hasAnyRole("ADMIN", "GERENTE")

                .requestMatchers(HttpMethod.GET,    "/api/v1/task-templates/**").authenticated()
                .requestMatchers(HttpMethod.GET,    "/api/v1/task-templates").authenticated()
                .requestMatchers(HttpMethod.POST,   "/api/v1/task-templates/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.PUT,    "/api/v1/task-templates/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/task-templates/**").hasAnyRole("ADMIN", "GERENTE")

                // ── Módulo Trainer / Exámenes (Sprint 19) ─────────────
                .requestMatchers(HttpMethod.POST,  "/api/v1/exams").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.GET,   "/api/v1/exams/store/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.GET,   "/api/v1/exams/*/submissions").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers(HttpMethod.GET,   "/api/v1/exams/**").authenticated()
                .requestMatchers(HttpMethod.POST,  "/api/v1/exams/*/submit").authenticated()

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
        List<String> origins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
