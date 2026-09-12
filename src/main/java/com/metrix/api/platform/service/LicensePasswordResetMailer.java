package com.metrix.api.platform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Envía la liga de reset. Si no hay SMTP configurado, solo registra la URL
 * (local / tests) y {@link #isConfigured()} queda en false.
 */
@Slf4j
@Service
public class LicensePasswordResetMailer {

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Mexico_City"));

    @Value("${metrix.mail.host:}")
    private String host;

    @Value("${metrix.mail.port:587}")
    private int port;

    @Value("${metrix.mail.username:}")
    private String username;

    @Value("${metrix.mail.password:}")
    private String password;

    @Value("${metrix.mail.from:metrixjm@gmail.com}")
    private String from;

    public boolean isConfigured() {
        return host != null && !host.isBlank();
    }

    public boolean sendResetLink(String email, String adminNombre, String empresaNombre,
                                 String resetUrl, Instant expiresAt) {
        String body = buildBody(adminNombre, empresaNombre, resetUrl, expiresAt);
        if (!isConfigured()) {
            log.info("[PasswordReset] SMTP desactivado; liga para {} ({}): {}", email, empresaNombre, resetUrl);
            return false;
        }
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(port);
            if (username != null && !username.isBlank()) {
                sender.setUsername(username);
                sender.setPassword(password);
            }
            Properties props = sender.getJavaMailProperties();
            props.put("mail.smtp.auth", String.valueOf(username != null && !username.isBlank()));
            props.put("mail.smtp.starttls.enable", "true");

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("METRIX — restablece tu contraseña");
            helper.setText(body, false);
            sender.send(message);
            log.info("[PasswordReset] correo enviado a {} ({})", mask(email), empresaNombre);
            return true;
        } catch (Exception e) {
            log.error("[PasswordReset] no se pudo enviar correo a {}: {}", mask(email), e.getMessage());
            return false;
        }
    }

    private String buildBody(String adminNombre, String empresaNombre, String resetUrl, Instant expiresAt) {
        String name = adminNombre == null || adminNombre.isBlank() ? "administrador" : adminNombre;
        String empresa = empresaNombre == null || empresaNombre.isBlank() ? "tu METRIX" : empresaNombre;
        String expiry = expiresAt == null ? "24 horas" : EXPIRY_FORMAT.format(expiresAt) + " (hora Centro)";
        return "Hola " + name + ",\n\n"
                + "Admin 0 aprobó el cambio de contraseña de " + empresa + ".\n\n"
                + "Abre esta liga para elegir una nueva (un solo uso, vence " + expiry + "):\n"
                + resetUrl + "\n\n"
                + "Si tú no pediste este cambio, ignora este correo.\n\n"
                + "METRIX\n";
    }

    private static String mask(String email) {
        if (email == null || email.isBlank()) return "***";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
