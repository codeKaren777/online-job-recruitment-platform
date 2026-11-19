package com.groupcc2.recruitment.application.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shortlist_candidates", schema = "recruitment")
public class ShortlistCandidate {
    @Id
    private UUID id;

    @Column(name = "application_id", updatable = false)
    private UUID applicationId;

    @Column(name = "decision_audit_id", nullable = false, updatable = false)
    private UUID decisionAuditId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "employer_id", nullable = false, updatable = false)
    private UUID employerId;

    @Column(name = "job_seeker_id", nullable = false, updatable = false)
    private UUID jobSeekerId;

    @Column(name = "job_title_snapshot", nullable = false, updatable = false)
    private String jobTitle;

    @Column(name = "display_name_snapshot", nullable = false, updatable = false)
    private String displayName;

    @Column(name = "full_name_snapshot", updatable = false)
    private String fullName;

    @Column(name = "date_of_birth_snapshot", updatable = false)
    private LocalDate dateOfBirth;

    @Column(name = "phone_snapshot", updatable = false)
    private String phone;

    @Column(name = "location_snapshot", updatable = false)
    private String location;

    @Column(name = "biography_snapshot", updatable = false)
    private String biography;

    @Column(name = "cv_document_id", updatable = false)
    private UUID cvDocumentId;

    @Column(name = "cv_filename_snapshot", updatable = false)
    private String cvFilename;

    @Column(name = "cv_content_type_snapshot", updatable = false)
    private String cvContentType;

    @Column(name = "cv_byte_size_snapshot", updatable = false)
    private Long cvByteSize;

    @Column(name = "shortlisted_at", nullable = false, updatable = false)
    private Instant shortlistedAt;

    @Column(name = "retained_at", nullable = false, updatable = false)
    private Instant retainedAt;

    protected ShortlistCandidate() {
    }

    public ShortlistCandidate(UUID applicationId, UUID decisionAuditId, UUID jobId, UUID employerId,
                              UUID jobSeekerId, String jobTitle,
                              String displayName, String fullName, LocalDate dateOfBirth,
                              String phone, String location, String biography, UUID cvDocumentId,
                              String cvFilename, String cvContentType, Long cvByteSize,
                              Instant shortlistedAt) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.decisionAuditId = decisionAuditId;
        this.jobId = jobId;
        this.employerId = employerId;
        this.jobSeekerId = jobSeekerId;
        this.jobTitle = jobTitle;
        this.displayName = displayName;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.phone = phone;
        this.location = location;
        this.biography = biography;
        this.cvDocumentId = cvDocumentId;
        this.cvFilename = cvFilename;
        this.cvContentType = cvContentType;
        this.cvByteSize = cvByteSize;
        this.shortlistedAt = shortlistedAt;
        this.retainedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getDecisionAuditId() { return decisionAuditId; }
    public UUID getJobId() { return jobId; }
    public UUID getEmployerId() { return employerId; }
    public UUID getJobSeekerId() { return jobSeekerId; }
    public String getJobTitle() { return jobTitle; }
    public String getDisplayName() { return displayName; }
    public String getFullName() { return fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getPhone() { return phone; }
    public String getLocation() { return location; }
    public String getBiography() { return biography; }
    public UUID getCvDocumentId() { return cvDocumentId; }
    public String getCvFilename() { return cvFilename; }
    public String getCvContentType() { return cvContentType; }
    public Long getCvByteSize() { return cvByteSize; }
    public Instant getShortlistedAt() { return shortlistedAt; }
    public Instant getRetainedAt() { return retainedAt; }
}
