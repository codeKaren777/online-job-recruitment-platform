package com.groupcc2.recruitment.application.notification;

import java.time.Instant;
import java.util.UUID;

import com.groupcc2.recruitment.application.entity.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Database-managed outbox record; writes and claims use constrained PostgreSQL functions. */
@Entity
@Table(name = "application_notification_outbox", schema = "recruitment")
public class ApplicationNotificationOutbox {
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "recipient_email", nullable = false, columnDefinition = "citext")
    private String recipientEmail;

    @Column(name = "applicant_name")
    private String applicantName;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(name = "company_name")
    private String companyName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "application_status", nullable = false, columnDefinition = "recruitment.application_status")
    private ApplicationStatus applicationStatus;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    protected ApplicationNotificationOutbox() {
    }
}
