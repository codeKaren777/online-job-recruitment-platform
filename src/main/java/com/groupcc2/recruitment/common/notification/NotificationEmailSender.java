package com.groupcc2.recruitment.common.notification;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class NotificationEmailSender {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ISO_INSTANT.withLocale(Locale.ROOT);

    private final JavaMailSender mailSender;
    private final String from;
    private final String resetUrl;

    public NotificationEmailSender(
            JavaMailSender mailSender,
            @Value("${app.mail.from:}") String from,
            @Value("${app.auth.password-reset-url:}") String resetUrl) {
        this.mailSender = mailSender;
        this.from = validateAddress(from, "APP_MAIL_FROM");
        this.resetUrl = resetUrl == null ? "" : resetUrl.trim();
    }

    public void sendPasswordReset(String recipient, String rawToken, Instant expiresAt) {
        String safeRecipient = validateAddress(recipient, "password reset recipient");
        String resetLink = resetUrl.isBlank() ? rawToken : resetUrl + rawToken;
        String text = "We received a request to reset your password.\n\n"
                + "Use this reset token before " + DATE_FORMAT.format(expiresAt) + ":\n"
                + rawToken + "\n\nReset link:\n" + resetLink
                + "\n\nIf you did not request this, you can ignore this email.";
        String html = "<p>We received a request to reset your password.</p>"
                + "<p>This link expires at " + escape(DATE_FORMAT.format(expiresAt)) + ".</p>"
                + (resetUrl.isBlank()
                        ? "<p>Reset token: <code>" + escape(rawToken) + "</code></p>"
                        : "<p><a href=\"" + escape(resetLink) + "\">Reset your password</a></p>")
                + "<p>If you did not request this, you can ignore this email.</p>";
        send(safeRecipient, "Password reset request", text, html);
    }

    public void sendEmployerDecision(String recipient, String companyName, String status, String reason) {
        String safeRecipient = validateAddress(recipient, "employer decision recipient");
        String explanation = "APPROVED".equals(status)
                ? "Your employer account request has been approved."
                : "Your employer account request has been rejected.";
        String text = explanation + "\n\nCompany: " + companyName
                + (reason == null || reason.isBlank() ? "" : "\nReason: " + reason);
        String html = "<p>" + escape(explanation) + "</p><p>Company: "
                + escape(companyName) + "</p>"
                + (reason == null || reason.isBlank() ? "" : "<p>Reason: " + escape(reason) + "</p>");
        send(safeRecipient, "Employer account request " + status.toLowerCase(Locale.ROOT),
                text, html);
    }

    private void send(String recipient, String subject, String text, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject.replaceAll("[\\r\\n]", " "));
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MessagingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Notification email could not be rendered", exception);
        }
    }

    private static String validateAddress(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be configured");
        }
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            return address.toUnicodeString();
        } catch (AddressException exception) {
            throw new IllegalStateException(property + " must be a valid email address", exception);
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
