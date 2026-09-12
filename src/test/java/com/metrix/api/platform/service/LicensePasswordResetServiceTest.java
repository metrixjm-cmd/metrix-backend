package com.metrix.api.platform.service;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.dto.PasswordResetConfirmRequest;
import com.metrix.api.dto.PasswordResetRequestBody;
import com.metrix.api.dto.PasswordResetRequestResponse;
import com.metrix.api.exception.TooManyLoginAttemptsException;
import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import com.metrix.api.platform.TenantDatabaseNames;
import com.metrix.api.platform.model.LicensePasswordResetRequest;
import com.metrix.api.platform.model.LicensePasswordResetStatus;
import com.metrix.api.platform.model.MetrixInstance;
import com.metrix.api.platform.model.PlatformUser;
import com.metrix.api.platform.repository.LicensePasswordResetRequestRepository;
import com.metrix.api.platform.repository.MetrixInstanceRepository;
import com.metrix.api.platform.repository.PlatformUserRepository;
import com.metrix.api.repository.UserRepository;
import com.metrix.api.security.LoginAttemptLimiter;
import com.metrix.api.security.PasswordResetRequestLimiter;
import com.metrix.api.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicensePasswordResetServiceTest {

    @Mock private LicensePasswordResetRequestRepository requestRepository;
    @Mock private MetrixInstanceRepository instanceRepository;
    @Mock private PlatformUserRepository platformUserRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantLoginResolver tenantLoginResolver;
    @Mock private NotificationService notificationService;
    @Mock private LicensePasswordResetMailer mailer;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginAttemptLimiter loginAttemptLimiter;
    @Mock private PasswordResetRequestLimiter requestLimiter;

    private LicensePasswordResetService service;

    @BeforeEach
    void setUp() {
        TenantDatabaseNames names = new TenantDatabaseNames(
                "mongodb://localhost:27017/metrix_db", "metrix_platform");
        service = new LicensePasswordResetService(
                requestRepository, instanceRepository, platformUserRepository, userRepository,
                tenantLoginResolver, names, notificationService, mailer, passwordEncoder,
                loginAttemptLimiter, requestLimiter);
        org.mockito.Mockito.lenient().when(requestLimiter.isBlocked(anyString(), anyString())).thenReturn(false);
    }

    @Test
    void request_unknownUser_returnsSilently() {
        when(tenantLoginResolver.resolve("TACOS-A3F2", "ADMIN001"))
                .thenReturn(TenantLoginResolver.LoginResolution.notFound());

        service.requestReset(body("TACOS-A3F2", "ADMIN001"));

        verify(requestLimiter).recordAttempt("TACOS-A3F2", "ADMIN001");
        verify(requestRepository, never()).save(any());
        verify(notificationService, never()).sendToUser(anyString(), any());
    }

    @Test
    void request_platformAdmin_isIgnored() {
        service.requestReset(body("METRIX", "ADMIN001"));

        verify(tenantLoginResolver, never()).resolve(anyString(), anyString());
        verify(requestRepository, never()).save(any());
    }

    @Test
    void request_gerente_isIgnored() {
        when(tenantLoginResolver.resolve("TACOS-A3F2", "GER001"))
                .thenReturn(TenantLoginResolver.LoginResolution.tenant(
                        "metrix_tenant_x", "inst-1", "Tacos", "TACOS-A3F2"));
        when(userRepository.findByNumeroUsuario("GER001")).thenReturn(Optional.of(
                User.builder().id("g1").numeroUsuario("GER001").activo(true)
                        .roles(Set.of(Role.GERENTE)).email("ger@tacos.test").build()));

        service.requestReset(body("TACOS-A3F2", "GER001"));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void request_tenantAdmin_createsPendingAndNotifiesAdmin0() {
        stubTenantAdmin();
        when(requestRepository.findFirstByCodigoEmpresaAndNumeroUsuarioAndStatus(
                "TACOS-A3F2", "ADMIN001", LicensePasswordResetStatus.PENDING))
                .thenReturn(Optional.empty());
        when(requestRepository.save(any())).thenAnswer(inv -> {
            LicensePasswordResetRequest r = inv.getArgument(0);
            r.setId("req-1");
            return r;
        });
        when(platformUserRepository.findAll()).thenReturn(List.of(
                PlatformUser.builder().numeroUsuario("ADMIN001").platformAdmin(true).activo(true).build()));
        when(userRepository.findByNumeroUsuario("ADMIN001")).thenReturn(
                Optional.of(tenantAdmin()),
                Optional.of(User.builder().id("plat-op").numeroUsuario("ADMIN001").build()));

        service.requestReset(body("TACOS-A3F2", "ADMIN001"));

        ArgumentCaptor<LicensePasswordResetRequest> captor =
                ArgumentCaptor.forClass(LicensePasswordResetRequest.class);
        verify(requestRepository).save(captor.capture());
        assertEquals(LicensePasswordResetStatus.PENDING, captor.getValue().getStatus());
        assertEquals("admin@tacos.test", captor.getValue().getDestinationEmail());

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationService).sendToUser(eq("plat-op"), event.capture());
        assertEquals("LICENSE_PASSWORD_RESET_REQUESTED", event.getValue().getType());
        assertEquals("inst-1", event.getValue().getInstanceId());
    }

    @Test
    void request_duplicatePending_refreshesInsteadOfInserting() {
        stubTenantAdmin();
        LicensePasswordResetRequest existing = LicensePasswordResetRequest.builder()
                .id("req-old")
                .status(LicensePasswordResetStatus.PENDING)
                .codigoEmpresa("TACOS-A3F2")
                .numeroUsuario("ADMIN001")
                .requestedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        when(requestRepository.findFirstByCodigoEmpresaAndNumeroUsuarioAndStatus(
                "TACOS-A3F2", "ADMIN001", LicensePasswordResetStatus.PENDING))
                .thenReturn(Optional.of(existing));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(platformUserRepository.findAll()).thenReturn(List.of());

        service.requestReset(body("TACOS-A3F2", "ADMIN001"));

        ArgumentCaptor<LicensePasswordResetRequest> captor =
                ArgumentCaptor.forClass(LicensePasswordResetRequest.class);
        verify(requestRepository).save(captor.capture());
        assertEquals("req-old", captor.getValue().getId());
        assertEquals(LicensePasswordResetStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    void request_blocked_throws429() {
        when(requestLimiter.isBlocked("TACOS-A3F2", "ADMIN001")).thenReturn(true);

        assertThrows(TooManyLoginAttemptsException.class,
                () -> service.requestReset(body("TACOS-A3F2", "ADMIN001")));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void approve_issuesLinkWithoutEmailWhenSmtpOff() {
        LicensePasswordResetRequest pending = pendingRequest();
        when(requestRepository.findById("req-1")).thenReturn(Optional.of(pending));
        when(requestRepository.findByCodigoEmpresaAndNumeroUsuarioAndStatus(
                anyString(), anyString(), eq(LicensePasswordResetStatus.APPROVED)))
                .thenReturn(List.of());
        when(mailer.isConfigured()).thenReturn(false);
        when(mailer.sendResetLink(any(), any(), any(), any(), any())).thenReturn(false);
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PasswordResetRequestResponse response = service.approve("req-1", "ADMIN001");

        assertEquals(LicensePasswordResetStatus.APPROVED, response.getStatus());
        assertNotNull(response.getResetUrl());
        assertTrue(response.getResetUrl().contains("/auth/reset-password?token="));
        assertEquals(Boolean.FALSE, response.getEmailSent());
        assertEquals("a***@tacos.test", response.getDestinationEmailMasked());
    }

    @Test
    void approve_withoutEmail_fails() {
        LicensePasswordResetRequest pending = pendingRequest();
        pending.setDestinationEmail(null);
        when(requestRepository.findById("req-1")).thenReturn(Optional.of(pending));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.approve("req-1", "ADMIN001"));
        assertTrue(ex.getMessage().contains("correo"));
    }

    @Test
    void approve_whenSmtpConfiguredAndSendFails_keepsPending() {
        when(requestRepository.findById("req-1")).thenReturn(Optional.of(pendingRequest()));
        when(mailer.isConfigured()).thenReturn(true);
        when(mailer.sendResetLink(any(), any(), any(), any(), any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.approve("req-1", "ADMIN001"));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void reject_marksRejected() {
        when(requestRepository.findById("req-1")).thenReturn(Optional.of(pendingRequest()));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reject("req-1", "ADMIN001", "No reconocemos esta solicitud.");

        ArgumentCaptor<LicensePasswordResetRequest> captor =
                ArgumentCaptor.forClass(LicensePasswordResetRequest.class);
        verify(requestRepository).save(captor.capture());
        assertEquals(LicensePasswordResetStatus.REJECTED, captor.getValue().getStatus());
        assertEquals("No reconocemos esta solicitud.", captor.getValue().getRejectReason());
    }

    @Test
    void confirm_updatesTenantPasswordAndConsumesToken() {
        LicensePasswordResetRequest approved = pendingRequest();
        approved.setStatus(LicensePasswordResetStatus.APPROVED);
        approved.setTokenHash(hashOf("token-abcdefghijklmnopqrstuvwxyz012345"));
        approved.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(requestRepository.findByTokenHash(anyString())).thenReturn(Optional.of(approved));
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(instance()));
        User admin = tenantAdmin();
        when(userRepository.findByNumeroUsuario("ADMIN001")).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("NuevaClave789")).thenReturn("encoded");
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.confirm(PasswordResetConfirmRequest.builder()
                .token("token-abcdefghijklmnopqrstuvwxyz012345")
                .newPassword("NuevaClave789")
                .confirmPassword("NuevaClave789")
                .build());

        assertEquals("encoded", admin.getPassword());
        verify(userRepository).save(admin);
        verify(loginAttemptLimiter).recordSuccess("TACOS-A3F2", "ADMIN001");
        ArgumentCaptor<LicensePasswordResetRequest> captor =
                ArgumentCaptor.forClass(LicensePasswordResetRequest.class);
        verify(requestRepository).save(captor.capture());
        assertEquals(LicensePasswordResetStatus.CONSUMED, captor.getValue().getStatus());
    }

    @Test
    void confirm_mismatch_is400() {
        assertThrows(IllegalArgumentException.class, () -> service.confirm(
                PasswordResetConfirmRequest.builder()
                        .token("token-abcdefghijklmnopqrstuvwxyz012345")
                        .newPassword("NuevaClave789")
                        .confirmPassword("otra")
                        .build()));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void confirm_unknownToken_is422() {
        when(requestRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.confirm(
                PasswordResetConfirmRequest.builder()
                        .token("token-abcdefghijklmnopqrstuvwxyz012345")
                        .newPassword("NuevaClave789")
                        .confirmPassword("NuevaClave789")
                        .build()));
        assertEquals(LicensePasswordResetService.INVALID_LINK, ex.getMessage());
    }

    @Test
    void maskEmail_keepsFirstCharAndDomain() {
        assertEquals("a***@tacos.test", LicensePasswordResetService.maskEmail("admin@tacos.test"));
        assertNull(LicensePasswordResetService.maskEmail(null));
    }

    private void stubTenantAdmin() {
        when(tenantLoginResolver.resolve("TACOS-A3F2", "ADMIN001"))
                .thenReturn(TenantLoginResolver.LoginResolution.tenant(
                        "metrix_tenant_x", "inst-1", "Tacos Norte", "TACOS-A3F2"));
        when(userRepository.findByNumeroUsuario("ADMIN001")).thenReturn(Optional.of(tenantAdmin()));
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(instance()));
    }

    private User tenantAdmin() {
        return User.builder()
                .id("u-admin")
                .numeroUsuario("ADMIN001")
                .nombre("Ana Pérez")
                .email("admin@tacos.test")
                .activo(true)
                .roles(Set.of(Role.ADMIN))
                .build();
    }

    private MetrixInstance instance() {
        return MetrixInstance.builder()
                .id("inst-1")
                .codigoEmpresa("TACOS-A3F2")
                .empresaNombre("Tacos Norte")
                .databaseName("metrix_tenant_x")
                .adminNumeroUsuario("ADMIN001")
                .adminNombre("Ana Pérez")
                .contactoEmail("contacto@tacos.test")
                .build();
    }

    private LicensePasswordResetRequest pendingRequest() {
        return LicensePasswordResetRequest.builder()
                .id("req-1")
                .instanceId("inst-1")
                .codigoEmpresa("TACOS-A3F2")
                .empresaNombre("Tacos Norte")
                .numeroUsuario("ADMIN001")
                .adminNombre("Ana Pérez")
                .destinationEmail("admin@tacos.test")
                .status(LicensePasswordResetStatus.PENDING)
                .requestedAt(Instant.now())
                .build();
    }

    private PasswordResetRequestBody body(String codigo, String numero) {
        return PasswordResetRequestBody.builder()
                .codigoEmpresa(codigo)
                .numeroUsuario(numero)
                .build();
    }

    private String hashOf(String token) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
