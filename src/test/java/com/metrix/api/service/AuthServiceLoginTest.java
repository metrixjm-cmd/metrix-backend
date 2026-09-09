package com.metrix.api.service;

import com.metrix.api.dto.AuthRequest;
import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import com.metrix.api.platform.EmpresaCodigos;
import com.metrix.api.platform.TenantDatabaseNames;
import com.metrix.api.platform.model.PlatformUser;
import com.metrix.api.platform.service.PlatformAdminService;
import com.metrix.api.platform.service.TenantLicenseGuard;
import com.metrix.api.platform.service.TenantLoginResolver;
import com.metrix.api.repository.StoreRepository;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.security.JwtService;
import com.metrix.api.security.LoginAttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private LoginAttemptLimiter loginAttemptLimiter;
    @Mock private TenantLoginResolver tenantLoginResolver;
    @Mock private TenantLicenseGuard tenantLicenseGuard;
    @Mock private PlatformAdminService platformAdminService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        TenantDatabaseNames names = new TenantDatabaseNames(
                "mongodb://localhost:27017/metrix_db", "metrix_platform");
        service = new AuthService(
                userRepository, storeRepository, passwordEncoder, jwtService,
                authenticationManager, loginAttemptLimiter, tenantLoginResolver,
                names, tenantLicenseGuard, platformAdminService);
    }

    @Test
    void unknownResolution_isBadCredentials() {
        when(loginAttemptLimiter.isBlocked("TACOS-A3F2", "ADMIN001")).thenReturn(false);
        when(tenantLoginResolver.resolve("TACOS-A3F2", "ADMIN001"))
                .thenReturn(TenantLoginResolver.LoginResolution.notFound());

        AuthRequest request = AuthRequest.builder()
                .codigoEmpresa("TACOS-A3F2")
                .numeroUsuario("ADMIN001")
                .password("secret1")
                .build();

        assertThrows(BadCredentialsException.class, () -> service.login(request));
        verify(loginAttemptLimiter).recordFailure("TACOS-A3F2", "ADMIN001");
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void suspendedTenant_doesNotIssueJwt() {
        when(loginAttemptLimiter.isBlocked("TACOS-A3F2", "ADMIN001")).thenReturn(false);
        when(tenantLoginResolver.resolve("TACOS-A3F2", "ADMIN001"))
                .thenReturn(TenantLoginResolver.LoginResolution.tenant(
                        "metrix_tenant_tacos_a3f2", "inst-1", "Tacos", "TACOS-A3F2"));
        when(platformAdminService.isSuspended("inst-1")).thenReturn(true);
        when(platformAdminService.suspensionMessage("inst-1")).thenReturn("suspendida");

        AuthRequest request = AuthRequest.builder()
                .codigoEmpresa("TACOS-A3F2")
                .numeroUsuario("ADMIN001")
                .password("secret1")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.login(request));
        assertEquals("suspendida", ex.getMessage());
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void platformLogin_putsMetrixCode() {
        when(loginAttemptLimiter.isBlocked(EmpresaCodigos.PLATFORM, "ADMIN001")).thenReturn(false);
        PlatformUser platformUser = PlatformUser.builder()
                .numeroUsuario("ADMIN001")
                .nombre("Admin 0")
                .password("hash")
                .roles(Set.of(Role.ADMIN))
                .activo(true)
                .build();
        when(tenantLoginResolver.resolve(EmpresaCodigos.PLATFORM, "ADMIN001"))
                .thenReturn(TenantLoginResolver.LoginResolution.platform(platformUser));
        when(passwordEncoder.matches("Admin123456", "hash")).thenReturn(true);
        when(jwtService.generateToken(any(), any())).thenReturn("jwt");

        AuthRequest request = AuthRequest.builder()
                .codigoEmpresa("METRIX")
                .numeroUsuario("ADMIN001")
                .password("Admin123456")
                .build();

        var response = service.login(request);

        assertTrue(response.isPlatformAdmin());
        assertEquals(EmpresaCodigos.PLATFORM, response.getCodigoEmpresa());
        assertEquals("jwt", response.getToken());
    }
}
