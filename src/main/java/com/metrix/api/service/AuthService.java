package com.metrix.api.service;

import com.metrix.api.dto.AuthRequest;
import com.metrix.api.dto.AuthResponse;
import com.metrix.api.exception.TooManyLoginAttemptsException;
import com.metrix.api.model.User;
import com.metrix.api.platform.TenantContext;
import com.metrix.api.platform.TenantDatabaseNames;
import com.metrix.api.platform.model.PlatformUser;
import com.metrix.api.platform.service.TenantLicenseGuard;
import com.metrix.api.platform.service.TenantLoginResolver;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.security.JwtService;
import com.metrix.api.security.LoginAttemptLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final TenantLoginResolver tenantLoginResolver;
    private final TenantDatabaseNames tenantDatabaseNames;
    private final TenantLicenseGuard tenantLicenseGuard;

    public AuthResponse login(AuthRequest request) {
        String numeroUsuario = request.getNumeroUsuario();

        if (loginAttemptLimiter.isBlocked(numeroUsuario)) {
            throw new TooManyLoginAttemptsException(
                    "Demasiados intentos fallidos. Vuelve a intentarlo en unos minutos.");
        }

        TenantLoginResolver.LoginResolution resolution = tenantLoginResolver.resolve(numeroUsuario);
        if (resolution.type() == TenantLoginResolver.LoginType.NOT_FOUND) {
            loginAttemptLimiter.recordFailure(numeroUsuario);
            throw new BadCredentialsException("Credenciales incorrectas");
        }

        try {
            if (resolution.type() == TenantLoginResolver.LoginType.PLATFORM) {
                return loginPlatformAdmin(resolution.platformUser(), request.getPassword(), numeroUsuario);
            }

            TenantContext.setPlatformAdmin(false);
            TenantContext.setDatabaseName(resolution.databaseName());
            TenantContext.setInstanceId(resolution.instanceId());

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(numeroUsuario, request.getPassword())
            );

            loginAttemptLimiter.recordSuccess(numeroUsuario);

            User user = userRepository.findByNumeroUsuario(numeroUsuario)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

            return buildTenantAuthResponse(user, resolution.databaseName(), resolution.instanceId(), false);
        } catch (AuthenticationException e) {
            loginAttemptLimiter.recordFailure(numeroUsuario);
            throw e;
        }
    }

    private AuthResponse loginPlatformAdmin(PlatformUser platformUser, String rawPassword,
                                            String numeroUsuario) {
        if (!passwordEncoder.matches(rawPassword, platformUser.getPassword())) {
            loginAttemptLimiter.recordFailure(numeroUsuario);
            throw new BadCredentialsException("Credenciales incorrectas");
        }

        loginAttemptLimiter.recordSuccess(numeroUsuario);
        String operationalDb = tenantDatabaseNames.getDefaultOperationalDatabase();
        TenantContext.setPlatformAdmin(true);
        TenantContext.setDatabaseName(operationalDb);

        Map<String, Object> extraClaims = new java.util.HashMap<>();
        extraClaims.put("roles", platformUser.getRoles());
        extraClaims.put("storeId", "");
        extraClaims.put("turno", "");
        extraClaims.put("nombre", platformUser.getNombre() != null ? platformUser.getNombre() : "");
        extraClaims.put("platformAdmin", true);
        extraClaims.put("databaseName", operationalDb);
        extraClaims.put("instanceId", "");

        org.springframework.security.core.userdetails.User userDetails =
                new org.springframework.security.core.userdetails.User(
                        platformUser.getNumeroUsuario(),
                        platformUser.getPassword(),
                        java.util.Collections.emptyList()
                );

        String token = jwtService.generateToken(extraClaims, userDetails);

        return AuthResponse.builder()
                .token(token)
                .numeroUsuario(platformUser.getNumeroUsuario())
                .nombre(platformUser.getNombre())
                .storeId(null)
                .storeName("")
                .turno(null)
                .roles(platformUser.getRoles())
                .platformAdmin(true)
                .databaseName(operationalDb)
                .instanceId(null)
                .licensedFeatures(null)
                .build();
    }

    private AuthResponse buildTenantAuthResponse(User user, String databaseName,
                                                 String instanceId, boolean platformAdmin) {
        String storeName = "";
        if (user.getStoreId() != null && !user.getStoreId().isBlank()) {
            storeName = storeRepository.findById(user.getStoreId())
                    .map(s -> s.getNombre() != null ? s.getNombre() : s.getCodigo())
                    .orElse("");
        }

        List<String> licensedFeatures = tenantLicenseGuard.resolveLicensedFeaturesOrUnrestricted();

        Map<String, Object> extraClaims = new java.util.HashMap<>();
        extraClaims.put("roles", user.getRoles());
        extraClaims.put("storeId", user.getStoreId() != null ? user.getStoreId() : "");
        extraClaims.put("turno", user.getTurno() != null ? user.getTurno() : "");
        extraClaims.put("nombre", user.getNombre() != null ? user.getNombre() : "");
        extraClaims.put("platformAdmin", platformAdmin);
        extraClaims.put("databaseName", databaseName != null ? databaseName : "");
        extraClaims.put("instanceId", instanceId != null ? instanceId : "");

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
                .platformAdmin(platformAdmin)
                .databaseName(databaseName)
                .instanceId(instanceId)
                .licensedFeatures(licensedFeatures)
                .build();
    }
}
