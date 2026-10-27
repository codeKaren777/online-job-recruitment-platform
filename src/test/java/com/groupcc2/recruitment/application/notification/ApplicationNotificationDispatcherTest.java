package com.groupcc2.recruitment.application.notification;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Multipart;
import jakarta.mail.BodyPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationNotificationDispatcherTest {
    @Mock JavaMailSender mailSender;
    @Mock ApplicationNotificationOutboxRepository outbox;

    private MimeMessage message;
    private ApplicationNotificationOutboxRepository.ApplicationNotification event;

    @BeforeEach
    void setUp() {
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        event = new ApplicationNotificationOutboxRepository.ApplicationNotification(
                UUID.randomUUID(), "SUBMITTED", "registered@example.com", "<Alex & Co>",
                "<Senior Developer>", "Example & Sons", "PENDING",
                Instant.parse("2026-10-27T08:00:00Z"), Instant.parse("2026-10-27T08:00:00Z"),
                1, UUID.randomUUID());
    }

    @Test
    void sendsProfessionalEmailToCapturedAddressAndEscapesHtmlValues() throws Exception {
        mailer().send(event);

        message.saveChanges();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("registered@example.com");
        assertThat(message.getSubject()).contains("Senior Developer");
        Multipart multipart = (Multipart) message.getContent();
        String html = findHtml(multipart);
        assertThat(html).contains("Hello &lt;Alex &amp; Co&gt;,")
                .contains("&lt;Senior Developer&gt;")
                .contains("Example &amp; Sons")
                .contains("PENDING")
                .contains("2026-10-27 UTC 08:00");
        assertThat(html).doesNotContain("<Senior Developer>");
        verify(mailSender).send(message);
    }

    @Test
    void successfulDeliveryMarksTheEventSent() {
        when(outbox.claimBatch(25)).thenReturn(java.util.List.of(event));
        when(outbox.markSent(event.eventId(), event.claimToken())).thenReturn(true);

        dispatcher().dispatchPending();

        verify(outbox).markSent(event.eventId(), event.claimToken());
        verify(outbox, never()).recordFailure(any(), any(), any(), anyBoolean());
    }

    @Test
    void smtpFailureRecordsRetryableFailureWithoutEscapingDispatcher() {
        when(outbox.claimBatch(25)).thenReturn(java.util.List.of(event));
        when(outbox.recordFailure(event.eventId(), event.claimToken(), "MAIL_DELIVERY_ERROR", false))
                .thenReturn(true);
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(MimeMessage.class));

        dispatcher().dispatchPending();

        verify(outbox).recordFailure(event.eventId(), event.claimToken(), "MAIL_DELIVERY_ERROR", false);
        verify(outbox, never()).markSent(any(), any());
    }

    @Test
    void authenticationFailureIsRecordedAsPermanent() {
        when(outbox.claimBatch(25)).thenReturn(java.util.List.of(event));
        when(outbox.recordFailure(event.eventId(), event.claimToken(), "MAIL_PERMANENT_ERROR", true))
                .thenReturn(true);
        doThrow(new MailAuthenticationException("credentials rejected"))
                .when(mailSender).send(any(MimeMessage.class));

        dispatcher().dispatchPending();

        verify(outbox).recordFailure(event.eventId(), event.claimToken(), "MAIL_PERMANENT_ERROR", true);
    }

    private ApplicationNotificationMailer mailer() {
        return new ApplicationNotificationMailer(
                mailSender, "notifications@example.com", "smtp.example.com",
                "smtp-user", "smtp-password", true);
    }

    private ApplicationNotificationDispatcher dispatcher() {
        return new ApplicationNotificationDispatcher(outbox, mailer());
    }

    private static String findHtml(Multipart multipart) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            Object content = part.getContent();
            if (part.isMimeType("text/html")) {
                return content.toString();
            }
            if (content instanceof Multipart nested) {
                String html = findHtml(nested);
                if (html != null) {
                    return html;
                }
            }
        }
        return null;
    }
}
