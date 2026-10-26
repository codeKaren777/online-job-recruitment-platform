package com.groupcc2.recruitment.company.dto;

import com.groupcc2.recruitment.company.entity.Company;
import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(UUID id, UUID employerId, String name, String description,
                              String contactEmail, String contactPhone, String location,
                              Instant createdAt, Instant updatedAt) {
    public static CompanyResponse from(Company company) {
        return new CompanyResponse(company.getId(), company.getEmployerId(), company.getName(),
                company.getDescription(), company.getContactEmail(), company.getContactPhone(),
                company.getLocation(), company.getCreatedAt(), company.getUpdatedAt());
    }
}
