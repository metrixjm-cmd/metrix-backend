package com.metrix.api.platform.service;

import com.metrix.api.dto.NotificationEvent;
import com.metrix.api.dto.PasswordResetConfirmRequest;
import com.metrix.api.dto.PasswordResetRequestBody;
import com.metrix.api.dto.PasswordResetRequestResponse;
import com.metrix.api.dto.PasswordResetValidateResponse;
import com.metrix.api.exception.ResourceNotFoundException;
import com.metrix.api.exception.TooManyLoginAttemptsException;
import com.metrix.api.model.Role;
import com.metrix.api.model.User;
import com.metrix.api.platform.EmpresaCodigos;
import com.metrix.api.platform.TenantContext;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicensePasswordResetService {

    static final String INVALID_LINK = "La liga no es válida o ya fue usada.";

    private final LicensePasswordResetRequestRepository requestRepository;
    private final MetrixInstanceRepository instanceRepository;
    private final PlatformUserRepository platformUserRepository;
    private final UserRepository userRepository;
    private final TenantLoginResolver tenantLoginResolver;
    private final TenantDatabaseNames tenantDatabaseNames;
    private final NotificationService notificationService;
    private final LicensePasswordResetMailer mailer;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final PasswordResetRequestLimiter requestLimiter;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${metrix.payments.frontend-base-url:http://localhost:4200}")
    private String frontendBaseUrl = "http://localhost:4200";

    @Value("${metrix.password-reset.ttl-hours:24}")
    private long ttlHours = 24;

    public void requestReset(PasswordResetRequestBody body) {
        String codigo = EmpresaCodigos.normalize(body.getCodigoEmpresa());
        String numero = body.getNumeroUsuario() == null ? "" : body.getNumeroUsuario().trim();

        if (requestLimiter.isBlocked(codigo, numero)) {
            throw new TooManyLoginAttemptsException(
                    "Demasiadas solicitudes. Vuelve a intentarlo en unos minutos.");
        }
        requestLimiter.recordAttempt(codigo, numero);

        if (EmpresaCodigos.isPlatform(codigo) || EmpresaCodigos.isBlank(codigo)) {
            return;
        }

        TenantLoginResolver.LoginResolution resolution = tenantLoginResolver.resolve(codigo, numero);
        if (resolution.type() != TenantLoginResolver.LoginType.TENANT
                || resolution.instanceId() == null || resolution.instanceId().isBlank()) {
            return;
        }

        User admin = withTenantDatabase(resolution.databaseName(), () ->
                userRepository.findByNumeroUsuario(numero)
                        .filter(User::isActivo)
                        .filter(this::isTenantAdmin)
                        .orElse(null));
        if (admin == null) {
            return;
        }

        MetrixInstance instance = instanceRepository.findById(resolution.instanceId()).orElse(null);
        if (instance == null) {
            return;
        }

        String email = firstEmail(admin.getEmail(), instance.getContactoEmail());
        Instant now = Instant.now();
        LicensePasswordResetRequest existing = requestRepository
                .findFirstByCodigoEmpresaAndNumeroUsuarioAndStatus(
                        instance.getCodigoEmpresa(), numero, LicensePasswordResetStatus.PENDING)
                .orElse(null);

        LicensePasswordResetRequest saved;
        if (existing != null) {
            existing.setRequestedAt(now);
            existing.setDestinationEmail(email);
            existing.setAdminNombre(admin.getNombre());
            existing.setEmpresaNombre(instance.getEmpresaNombre());
            existing.setInstanceId(instance.getId());
            saved = requestRepository.save(existing);
        } else {
            saved = requestRepository.save(LicensePasswordResetRequest.builder()
                    .instanceId(instance.getId())
                    .codigoEmpresa(instance.getCodigoEmpresa())
                    .empresaNombre(instance.getEmpresaNombre())
                    .numeroUsuario(numero)
                    .adminNombre(admin.getNombre())
                    .destinationEmail(email)
                    .status(LicensePasswordResetStatus.PENDING)
                    .requestedAt(now)
                    .build());
        }
        notifyPlatformAdmins(saved);
    }

    public List<PasswordResetRequestResponse> list(LicensePasswordResetStatus status, String instanceId) {
        List<LicensePasswordResetRequest> rows = instanceId != null && !instanceId.isBlank()
                ? requestRepository.findByInstanceIdOrderByRequestedAtDesc(instanceId)
                : requestRepository.findAllByOrderByRequestedAtDesc();
        return rows.stream()
                .map(this::expireIfNeeded)
                .filter(r -> status == null || effectiveStatus(r) == status)
                .sorted(Comparator
                        .comparing((LicensePasswordResetRequest r) ->
                                effectiveStatus(r) != LicensePasswordResetStatus.PENDING)
                        .thenComparing(LicensePasswordResetRequest::getRequestedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(r -> toResponse(r, null, null))
                .toList();
    }

    public PasswordResetRequestResponse approve(String id, String resolvedBy) {
        LicensePasswordResetRequest request = findOrThrow(id);
        if (effectiveStatus(request) != LicensePasswordResetStatus.PENDING) {
            throw new IllegalStateException("Esta solicitud ya fue resuelta.");
        }
        return issueLink(request, resolvedBy);
    }

    public void reject(String id, String resolvedBy, String reason) {
        LicensePasswordResetRequest request = findOrThrow(id);
        if (effectiveStatus(request) != LicensePasswordResetStatus.PENDING) {
            throw new IllegalStateException("Esta solicitud ya fue resuelta.");
        }
        request.setStatus(LicensePasswordResetStatus.REJECTED);
        request.setResolvedAt(Instant.now());
        request.setResolvedBy(resolvedBy);
        request.setRejectReason(reason == null || reason.isBlank() ? null : reason.trim());
        requestRepository.save(request);
    }

    public PasswordResetRequestResponse initiateForInstance(String instanceId, String resolvedBy) {
        MetrixInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instancia no encontrada: " + instanceId));
        if (instance.getAdminNumeroUsuario() == null || instance.getAdminNumeroUsuario().isBlank()) {
            throw new IllegalStateException("Esta instancia no tiene un administrador.");
        }

        User admin = withTenantDatabase(instance.getDatabaseName(), () ->
                userRepository.findByNumeroUsuario(instance.getAdminNumeroUsuario())
                        .filter(User::isActivo)
                        .filter(this::isTenantAdmin)
                        .orElse(null));
        if (admin == null) {
            throw new IllegalStateException("No hay un administrador activo en esta licencia.");
        }

        closeOpenRequests(instance.getCodigoEmpresa(), instance.getAdminNumeroUsuario());

        LicensePasswordResetRequest created = requestRepository.save(LicensePasswordResetRequest.builder()
                .instanceId(instance.getId())
                .codigoEmpresa(instance.getCodigoEmpresa())
                .empresaNombre(instance.getEmpresaNombre())
                .numeroUsuario(instance.getAdminNumeroUsuario())
                .adminNombre(admin.getNombre() != null ? admin.getNombre() : instance.getAdminNombre())
                .destinationEmail(firstEmail(admin.getEmail(), instance.getContactoEmail()))
                .status(LicensePasswordResetStatus.PENDING)
                .requestedAt(Instant.now())
                .build());
        return issueLink(created, resolvedBy);
    }

    public PasswordResetValidateResponse validateToken(String token) {
        LicensePasswordResetRequest request = requireApprovedToken(token);
        return PasswordResetValidateResponse.builder()
                .valid(true)
                .codigoEmpresa(request.getCodigoEmpresa())
                .empresaNombre(request.getEmpresaNombre())
                .numeroUsuario(request.getNumeroUsuario())
                .expiresAt(request.getExpiresAt())
                .build();
    }

    public void confirm(PasswordResetConfirmRequest body) {
        if (!body.getNewPassword().equals(body.getConfirmPassword())) {
            throw new IllegalArgumentException("La nueva contraseña y la confirmación no coinciden.");
        }
        LicensePasswordResetRequest request = requireApprovedToken(body.getToken());
        MetrixInstance instance = instanceRepository.findById(request.getInstanceId())
                .orElseThrow(() -> new IllegalStateException(INVALID_LINK));

        withTenantDatabase(instance.getDatabaseName(), () -> {
            User user = userRepository.findByNumeroUsuario(request.getNumeroUsuario())
                    .orElseThrow(() -> new IllegalStateException(INVALID_LINK));
            user.setPassword(passwordEncoder.encode(body.getNewPassword()));
            userRepository.save(user);
            return null;
        });

        request.setStatus(LicensePasswordResetStatus.CONSUMED);
        request.setConsumedAt(Instant.now());
        requestRepository.save(request);
        loginAttemptLimiter.recordSuccess(request.getCodigoEmpresa(), request.getNumeroUsuario());
    }

    private PasswordResetRequestResponse issueLink(LicensePasswordResetRequest request, String resolvedBy) {
        String email = request.getDestinationEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Esta licencia no tiene un correo para enviar la liga.");
        }

        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(ttlHours));
        String resetUrl = frontendBase() + "/auth/reset-password?token=" + rawToken;

        boolean sent = mailer.sendResetLink(
                email, request.getAdminNombre(), request.getEmpresaNombre(), resetUrl, expiresAt);
        if (mailer.isConfigured() && !sent) {
            throw new IllegalStateException("No se pudo enviar el correo. Inténtalo de nuevo.");
        }

        expirePreviousApproved(request.getCodigoEmpresa(), request.getNumeroUsuario(), request.getId());

        request.setTokenHash(hashToken(rawToken));
        request.setStatus(LicensePasswordResetStatus.APPROVED);
        request.setExpiresAt(expiresAt);
        request.setResolvedAt(Instant.now());
        request.setResolvedBy(resolvedBy);
        LicensePasswordResetRequest saved = requestRepository.save(request);
        return toResponse(saved, resetUrl, sent);
    }

    private LicensePasswordResetRequest requireApprovedToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(INVALID_LINK);
        }
        LicensePasswordResetRequest request = requestRepository.findByTokenHash(hashToken(token.trim()))
                .orElseThrow(() -> new IllegalStateException(INVALID_LINK));
        if (effectiveStatus(expireIfNeeded(request)) != LicensePasswordResetStatus.APPROVED) {
            throw new IllegalStateException(INVALID_LINK);
        }
        return request;
    }

    private void expirePreviousApproved(String codigo, String numero, String keepId) {
        requestRepository.findByCodigoEmpresaAndNumeroUsuarioAndStatus(
                        codigo, numero, LicensePasswordResetStatus.APPROVED)
                .forEach(r -> {
                    if (keepId != null && keepId.equals(r.getId())) return;
                    r.setStatus(LicensePasswordResetStatus.EXPIRED);
                    requestRepository.save(r);
                });
    }

    private void closeOpenRequests(String codigo, String numero) {
        requestRepository.findByCodigoEmpresaAndNumeroUsuarioAndStatus(
                        codigo, numero, LicensePasswordResetStatus.PENDING)
                .forEach(r -> {
                    r.setStatus(LicensePasswordResetStatus.EXPIRED);
                    requestRepository.save(r);
                });
        expirePreviousApproved(codigo, numero, null);
    }

    private void notifyPlatformAdmins(LicensePasswordResetRequest request) {
        NotificationEvent event = NotificationEvent.builder()
                .id(UUID.randomUUID().toString())
                .type("LICENSE_PASSWORD_RESET_REQUESTED")
                .severity("warning")
                .title("Solicitud de contraseña")
                .body((request.getAdminNombre() == null ? request.getNumeroUsuario() : request.getAdminNombre())
                        + " (" + request.getNumeroUsuario() + ") de "
                        + request.getEmpresaNombre() + " olvidó su contraseña.")
                .instanceId(request.getInstanceId())
                .passwordResetRequestId(request.getId())
                .timestamp(Instant.now())
                .build();

        List<PlatformUser> admins = platformUserRepository.findAll().stream()
                .filter(PlatformUser::isPlatformAdmin)
                .filter(PlatformUser::isActivo)
                .toList();

        withTenantDatabase(tenantDatabaseNames.getDefaultOperationalDatabase(), () -> {
            for (PlatformUser platformUser : admins) {
                userRepository.findByNumeroUsuario(platformUser.getNumeroUsuario())
                        .ifPresent(user -> notificationService.sendToUser(user.getId(), event));
            }
            return null;
        });
    }

    private LicensePasswordResetRequest expireIfNeeded(LicensePasswordResetRequest request) {
        if (request.getStatus() == LicensePasswordResetStatus.APPROVED
                && request.getExpiresAt() != null
                && Instant.now().isAfter(request.getExpiresAt())) {
            request.setStatus(LicensePasswordResetStatus.EXPIRED);
            return requestRepository.save(request);
        }
        return request;
    }

    private LicensePasswordResetStatus effectiveStatus(LicensePasswordResetRequest request) {
        return request.getStatus();
    }

    private LicensePasswordResetRequest findOrThrow(String id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada: " + id));
    }

    private PasswordResetRequestResponse toResponse(LicensePasswordResetRequest request,
                                                    String resetUrl, Boolean emailSent) {
        return PasswordResetRequestResponse.builder()
                .id(request.getId())
                .instanceId(request.getInstanceId())
                .codigoEmpresa(request.getCodigoEmpresa())
                .empresaNombre(request.getEmpresaNombre())
                .numeroUsuario(request.getNumeroUsuario())
                .adminNombre(request.getAdminNombre())
                .destinationEmailMasked(maskEmail(request.getDestinationEmail()))
                .status(effectiveStatus(request))
                .requestedAt(request.getRequestedAt())
                .expiresAt(request.getExpiresAt())
                .resolvedAt(request.getResolvedAt())
                .resolvedBy(request.getResolvedBy())
                .consumedAt(request.getConsumedAt())
                .rejectReason(request.getRejectReason())
                .resetUrl(resetUrl)
                .emailSent(emailSent)
                .build();
    }

    private boolean isTenantAdmin(User user) {
        return user.getRoles() != null && user.getRoles().contains(Role.ADMIN);
    }

    private String firstEmail(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim().toLowerCase();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim().toLowerCase();
        }
        return null;
    }

    static String maskEmail(String email) {
        if (email == null || email.isBlank()) return null;
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token de reset.");
        }
    }

    private String frontendBase() {
        String base = frontendBaseUrl == null ? "http://localhost:4200" : frontendBaseUrl.trim();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private <T> T withTenantDatabase(String databaseName, Supplier<T> action) {
        String previousDb = TenantContext.getDatabaseName();
        boolean previousPlatform = TenantContext.isPlatformAdmin();
        String previousInstance = TenantContext.getInstanceId();
        String previousCodigo = TenantContext.getCodigoEmpresa();
        try {
            TenantContext.setPlatformAdmin(false);
            TenantContext.setDatabaseName(databaseName);
            return action.get();
        } finally {
            TenantContext.setDatabaseName(previousDb);
            TenantContext.setPlatformAdmin(previousPlatform);
            TenantContext.setInstanceId(previousInstance);
            TenantContext.setCodigoEmpresa(previousCodigo);
        }
    }
}
