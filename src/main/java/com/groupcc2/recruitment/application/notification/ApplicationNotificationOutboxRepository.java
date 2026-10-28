package com.groupcc2.recruitment.application.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationNotificationOutboxRepository {
    private final JdbcTemplate jdbc;

    public ApplicationNotificationOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean enqueue(UUID eventId, UUID applicationId, String eventType) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT recruitment.enqueue_application_notification(?, ?, ?)",
                Boolean.class, eventId, applicationId, eventType));
    }

    public List<ApplicationNotification> claimBatch(int limit) {
        return jdbc.query("SELECT * FROM recruitment.claim_application_notifications(?)",
                (rs, row) -> new ApplicationNotification(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("recipient_email"),
                        rs.getString("applicant_name"),
                        rs.getString("job_title"),
                        rs.getString("company_name"),
                        rs.getString("application_status"),
                        rs.getTimestamp("submitted_at").toInstant(),
                        rs.getTimestamp("event_at").toInstant(),
                        rs.getInt("attempt_count"),
                        rs.getObject("claim_token", UUID.class)),
                limit);
    }

    public boolean markSent(UUID eventId, UUID claimToken) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT recruitment.mark_application_notification_sent(?, ?)",
                Boolean.class, eventId, claimToken));
    }

    public boolean recordFailure(UUID eventId, UUID claimToken, String safeErrorCode, boolean permanent) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT recruitment.record_application_notification_failure(?, ?, ?, ?)",
                Boolean.class, eventId, claimToken, safeErrorCode, permanent));
    }

    public record ApplicationNotification(
            UUID eventId,
            String eventType,
            String recipientEmail,
            String applicantName,
            String jobTitle,
            String companyName,
            String applicationStatus,
            Instant submittedAt,
            Instant eventAt,
            int attemptCount,
            UUID claimToken) {
    }
}
