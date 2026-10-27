package com.groupcc2.recruitment.application.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "application_decision_audits", schema = "recruitment")
public class ApplicationDecisionAudit {
    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "employer_id", nullable = false, updatable = false)
    private UUID employerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "previous_status", nullable = false, columnDefinition = "recruitment.application_status")
    private ApplicationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "new_status", nullable = false, columnDefinition = "recruitment.application_status")
    private ApplicationStatus newStatus;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected ApplicationDecisionAudit() {
    }

    public ApplicationDecisionAudit(UUID applicationId, UUID jobId, UUID employerId,
                                   ApplicationStatus previousStatus, ApplicationStatus newStatus,
                                   Instant decidedAt) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.jobId = jobId;
        this.employerId = employerId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.decidedAt = decidedAt;
    }

    public UUID getId() {
        return id;
    }

    @PrePersist
    void initializeIdAndTimestamp() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (decidedAt == null) {
            decidedAt = Instant.now();
        }
    }
}
