package com.groupcc2.recruitment.users.employer.dto;

import com.groupcc2.recruitment.users.employer.entity.EmployerRequestStatus;
import com.groupcc2.recruitment.users.employer.entity.EmployerUpgradeRequest;
import java.time.Instant;
import java.util.UUID;

public record EmployerUpgradeResponse(UUID id, UUID userId, String companyName,
                                      String businessDescription, String contactEmail,
                                      String contactPhone, String location,
                                      EmployerRequestStatus status, UUID reviewedBy,
                                      Instant reviewedAt, String rejectionReason,
                                      Instant createdAt, Instant updatedAt, String vatNumber,
                                      String registrationNumber, UUID companyId,
                                      String documentOriginalFilename, String documentContentType,
                                      long documentSize) {
    public static EmployerUpgradeResponse from(EmployerUpgradeRequest request) {
        return new EmployerUpgradeResponse(request.getId(), request.getUserId(), request.getCompanyName(),
                request.getBusinessDescription(), request.getContactEmail(), request.getContactPhone(),
                request.getLocation(), request.getStatus(), request.getReviewedBy(), request.getReviewedAt(),
                request.getRejectionReason(), request.getCreatedAt(), request.getUpdatedAt(),
                request.getVatNumber(), request.getRegistrationNumber(), request.getCompanyId(),
                request.getDocumentOriginalFilename(), request.getDocumentContentType(), request.getDocumentSize());
    }
}
