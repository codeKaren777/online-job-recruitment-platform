package com.groupcc2.recruitment.application.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A job seeker's single application to a job posting.
 *
 * Jobs and users are referenced by id only so this module does not depend on other modules' entities.
 * The database owns the UNIQUE (job_id, job_seeker_id) rule and the PENDING-on-insert rule; this class
 * mirrors them so the service layer fails early with a clear error.
 */
@Entity
@Table(name = "applications", schema = "recruitment")
public class Application {
    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "job_seeker_id", nullable = false, updatable = false)
    private UUID jobSeekerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "recruitment.application_status")
    private ApplicationStatus status;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "shortlisted_at")
    private Instant shortlistedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    protected Application() {
    }

    private Application(UUID jobId, UUID jobSeekerId) {
        this.id = UUID.randomUUID();
        this.jobId = jobId;
        this.jobSeekerId = jobSeekerId;
        this.status = ApplicationStatus.PENDING; // BR-03
    }

    public static Application submit(UUID jobId, UUID jobSeekerId) {
        return new Application(jobId, jobSeekerId);
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        if (submittedAt == null) {
            submittedAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getJobSeekerId() {
        return jobSeekerId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getShortlistedAt() {
        return shortlistedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public void changeStatus(ApplicationStatus newStatus, Instant changedAt) {
        if (newStatus == null || newStatus == ApplicationStatus.PENDING || status != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Only pending applications can be shortlisted or rejected");
        }
        status = newStatus;
        if (newStatus == ApplicationStatus.SHORTLISTED) {
            shortlistedAt = changedAt;
        } else {
            rejectedAt = changedAt;
        }
    }
}
