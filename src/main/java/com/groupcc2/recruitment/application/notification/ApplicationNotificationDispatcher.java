package com.groupcc2.recruitment.application.notification;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class ApplicationNotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ApplicationNotificationDispatcher.class);

    private final ApplicationNotificationOutboxRepository outbox;
    private final ApplicationNotificationMailer mailer;

    public ApplicationNotificationDispatcher(ApplicationNotificationOutboxRepository outbox,
                                              ApplicationNotificationMailer mailer) {
        this.outbox = outbox;
        this.mailer = mailer;
    }

    @Scheduled(fixedDelayString = "${app.mail.poll-interval-ms:30000}")
    public void dispatchPending() {
        List<ApplicationNotificationOutboxRepository.ApplicationNotification> batch = outbox.claimBatch(25);
        for (var event : batch) {
            deliver(event);
        }
    }

    private void deliver(ApplicationNotificationOutboxRepository.ApplicationNotification event) {
        try {
            mailer.send(event);
            if (!outbox.markSent(event.eventId(), event.claimToken())) {
                log.warn("Notification claim expired before completion for event {}", event.eventId());
            }
        } catch (PermanentNotificationException exception) {
            recordFailure(event, "PERMANENT_CONTENT_ERROR", true);
        } catch (MailException exception) {
            boolean permanent = ApplicationNotificationMailer.isPermanentFailure(exception);
            recordFailure(event, permanent ? "MAIL_PERMANENT_ERROR" : "MAIL_DELIVERY_ERROR", permanent);
        }
    }

    private void recordFailure(ApplicationNotificationOutboxRepository.ApplicationNotification event,
                               String safeCode, boolean permanent) {
        if (!outbox.recordFailure(event.eventId(), event.claimToken(), safeCode, permanent)) {
            log.warn("Notification claim expired before failure recording for event {}", event.eventId());
        } else if (permanent || event.attemptCount() >= 5) {
            log.error("Application notification permanently failed: event={} code={}", event.eventId(), safeCode);
        } else {
            log.warn("Application notification delivery failed; retry scheduled: event={} code={}",
                    event.eventId(), safeCode);
        }
    }
}
