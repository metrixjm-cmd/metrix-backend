package com.metrix.api.security;

import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.TenantDatabaseNames;
import com.metrix.api.platform.model.PlatformUser;
import com.metrix.api.platform.repository.PlatformUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final PlatformUserRepository platformUserRepository;
    private final TenantDatabaseNames tenantDatabaseNames;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String numeroUsuario = jwtService.extractUsername(jwt);

        if (numeroUsuario != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            applyTenantContextFromJwt(jwt);

            UserDetails userDetails = loadUserDetails(jwt, numeroUsuario);

            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void applyTenantContextFromJwt(String jwt) {
        Boolean platformAdmin = jwtService.extractClaim(jwt, claims -> {
            Object value = claims.get("platformAdmin");
            return value instanceof Boolean b ? b : Boolean.FALSE;
        });
        String databaseName = jwtService.extractClaim(jwt, claims -> {
            Object value = claims.get("databaseName");
            return value instanceof String s ? s : null;
        });
        String instanceId = jwtService.extractClaim(jwt, claims -> {
            Object value = claims.get("instanceId");
            return value instanceof String s ? s : null;
        });

        TenantContext.setPlatformAdmin(Boolean.TRUE.equals(platformAdmin));
        TenantContext.setDatabaseName(
                tenantDatabaseNames.resolveOperationalDatabase(databaseName, Boolean.TRUE.equals(platformAdmin)));
        TenantContext.setInstanceId(instanceId);
    }

    private UserDetails loadUserDetails(String jwt, String numeroUsuario) {
        if (Boolean.TRUE.equals(jwtService.extractClaim(jwt, claims -> claims.get("platformAdmin")))) {
            PlatformUser platformUser = platformUserRepository.findByNumeroUsuario(numeroUsuario)
                    .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(
                            "Usuario plataforma no encontrado: " + numeroUsuario));

            var authorities = platformUser.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                    .collect(Collectors.toCollection(java.util.HashSet::new));
            authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));

            return org.springframework.security.core.userdetails.User.builder()
                    .username(platformUser.getNumeroUsuario())
                    .password(platformUser.getPassword())
                    .authorities(authorities)
                    .disabled(!platformUser.isActivo())
                    .build();
        }

        return userDetailsService.loadUserByUsername(numeroUsuario);
    }
}
