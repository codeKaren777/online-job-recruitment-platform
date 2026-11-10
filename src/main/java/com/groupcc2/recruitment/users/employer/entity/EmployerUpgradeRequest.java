package com.groupcc2.recruitment.users.employer.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "employer_upgrade_requests", schema = "recruitment")
public class EmployerUpgradeRequest {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "business_description", nullable = false)
    private String businessDescription;

    @Column(name = "contact_email", nullable = false, columnDefinition = "citext")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(nullable = false)
    private String location;

    @Column(name = "vat_number", nullable = false)
    private String vatNumber;

    @Column(name = "registration_number", nullable = false)
    private String registrationNumber;

    @Column(name = "document_storage_key", nullable = false)
    private String documentStorageKey;

    @Column(name = "document_original_filename", nullable = false)
    private String documentOriginalFilename;

    @Column(name = "document_content_type", nullable = false)
    private String documentContentType;

    @Column(name = "document_size", nullable = false)
    private long documentSize;

    @Column(name = "company_id")
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "recruitment.employer_request_status")
    private EmployerRequestStatus status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployerUpgradeRequest() {}

    public EmployerUpgradeRequest(UUID userId, String companyName, String businessDescription,
                                  String contactEmail, String contactPhone, String location,
                                  String vatNumber, String registrationNumber, String documentStorageKey,
                                  String documentOriginalFilename, String documentContentType, long documentSize) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.companyName = companyName;
        this.businessDescription = businessDescription;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.location = location;
        this.vatNumber = vatNumber;
        this.registrationNumber = registrationNumber;
        this.documentStorageKey = documentStorageKey;
        this.documentOriginalFilename = documentOriginalFilename;
        this.documentContentType = documentContentType;
        this.documentSize = documentSize;
        this.status = EmployerRequestStatus.PENDING;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void approve(UUID administratorId) {
        requirePending();
        status = EmployerRequestStatus.APPROVED;
        reviewedBy = administratorId;
        reviewedAt = Instant.now();
        updatedAt = reviewedAt;
    }

    public void attachCompany(UUID companyId) { this.companyId = companyId; }

    public void reject(UUID administratorId, String reason) {
        requirePending();
        status = EmployerRequestStatus.REJECTED;
        reviewedBy = administratorId;
        rejectionReason = reason;
        reviewedAt = Instant.now();
        updatedAt = reviewedAt;
    }

    private void requirePending() {
        if (status != EmployerRequestStatus.PENDING) {
            throw new IllegalStateException("Employer request has already been reviewed");
        }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getCompanyName() { return companyName; }
    public String getBusinessDescription() { return businessDescription; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getLocation() { return location; }
    public EmployerRequestStatus getStatus() { return status; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getVatNumber() { return vatNumber; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getDocumentStorageKey() { return documentStorageKey; }
    public String getDocumentOriginalFilename() { return documentOriginalFilename; }
    public String getDocumentContentType() { return documentContentType; }
    public long getDocumentSize() { return documentSize; }
    public UUID getCompanyId() { return companyId; }
}
