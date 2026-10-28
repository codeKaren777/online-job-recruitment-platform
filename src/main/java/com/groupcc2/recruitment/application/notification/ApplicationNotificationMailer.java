package com.groupcc2.recruitment.application.notification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class ApplicationNotificationMailer {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd 'UTC' HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;
    private final String from;

    public ApplicationNotificationMailer(
            JavaMailSender mailSender,
            @Value("${app.mail.from:}") String from,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean smtpAuth) {
        this.mailSender = mailSender;
        this.from = validateAddress(from, "APP_MAIL_FROM");
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("SPRING_MAIL_HOST is required when application mail is enabled");
        }
        if (smtpAuth && ((username == null || username.isBlank()) || (password == null || password.isBlank()))) {
            throw new IllegalStateException(
                    "SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD are required when SMTP authentication is enabled");
        }
    }

    public void send(ApplicationNotificationOutboxRepository.ApplicationNotification event) {
        String status = event.applicationStatus();
        if (!status.equals("PENDING") && !status.equals("SHORTLISTED") && !status.equals("REJECTED")) {
            throw new PermanentNotificationException("Unsupported application status", null);
        }
        if (event.companyName() == null || event.companyName().isBlank()) {
            throw new PermanentNotificationException("Company information is unavailable", null);
        }
        try {
            String html = readTemplate(status)
                    .replace("{{greeting}}", event.applicantName() == null || event.applicantName().isBlank()
                            ? "Hello" : "Hello " + escape(event.applicantName()))
                    .replace("{{jobTitle}}", escape(event.jobTitle()))
                    .replace("{{companyName}}", escape(event.companyName()))
                    .replace("{{applicationStatus}}", escape(status))
                    .replace("{{submittedAt}}", escape(DATE_FORMAT.format(event.submittedAt())))
                    .replace("{{eventAt}}", escape(DATE_FORMAT.format(event.eventAt())));
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(validateAddress(event.recipientEmail(), "notification recipient"));
            helper.setSubject(subject(status, event.jobTitle()));
            helper.setText(plainText(event), html);
            mailSender.send(message);
        } catch (IOException | MessagingException | IllegalArgumentException exception) {
            throw new PermanentNotificationException("Application notification could not be rendered", exception);
        }
    }

    static boolean isPermanentFailure(MailException exception) {
        if (exception instanceof MailAuthenticationException) {
            return true;
        }
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SendFailedException || cause instanceof AddressException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static String readTemplate(String status) throws IOException {
        String path = "templates/email/application-" + status.toLowerCase(Locale.ROOT) + ".html";
        try (var input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String subject(String status, String jobTitle) {
        String safeTitle = jobTitle.replaceAll("[\\r\\n]", " ");
        return switch (status) {
            case "PENDING" -> "Application received - " + safeTitle;
            case "SHORTLISTED" -> "Application update: shortlisted - " + safeTitle;
            case "REJECTED" -> "Application update - " + safeTitle;
            default -> throw new IllegalArgumentException("Unsupported application status");
        };
    }

    private static String plainText(ApplicationNotificationOutboxRepository.ApplicationNotification event) {
        String greeting = event.applicantName() == null || event.applicantName().isBlank()
                ? "Hello," : "Hello " + event.applicantName() + ",";
        String explanation = switch (event.applicationStatus()) {
            case "PENDING" -> "We have received your application.";
            case "SHORTLISTED" -> "The employer has shortlisted your application.";
            case "REJECTED" -> "The employer has completed its review of your application. "
                    + "We appreciate the time you took to apply.";
            default -> throw new IllegalArgumentException("Unsupported application status");
        };
        return greeting + "\n\n" + explanation + "\n\n"
                + "Job: " + event.jobTitle() + "\nCompany: " + event.companyName()
                + "\nApplication status: " + event.applicationStatus()
                + "\nSubmitted: " + DATE_FORMAT.format(event.submittedAt())
                + "\nStatus updated: " + DATE_FORMAT.format(event.eventAt())
                + "\n\nSign in to the recruitment platform to review your application status."
                + "\n\nRegards,\nThe Recruitment Platform";
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
