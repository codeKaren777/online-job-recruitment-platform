package com.groupcc2.recruitment.common.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class NotificationEmailSenderTest {
    @Mock
    private JavaMailSender mailSender;

    private MimeMessage message;

    @BeforeEach
    void setUp() {
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
    }

    @Test
    void sendsPasswordResetTokenToTheAccountEmail() throws Exception {
        NotificationEmailSender sender =
                new NotificationEmailSender(mailSender, "notifications@example.com",
                        "https://example.com/reset?token=");

        sender.sendPasswordReset("person@example.com", "token-value",
                Instant.parse("2026-10-30T08:00:00Z"));

        message.saveChanges();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("person@example.com");
        assertThat(message.getSubject()).isEqualTo("Password reset request");
        assertThat(message.getContentType()).startsWith("multipart/mixed");
        verify(mailSender).send(message);
    }

    @Test
    void sendsEmployerDecisionWithRejectionReason() throws Exception {
        NotificationEmailSender sender =
                new NotificationEmailSender(mailSender, "notifications@example.com", "");

        sender.sendEmployerDecision("person@example.com", "Acme", "REJECTED", "Missing documents");

        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("Employer account request rejected");
        assertThat(message.getContentType()).startsWith("multipart/mixed");
        verify(mailSender).send(message);
    }
}
