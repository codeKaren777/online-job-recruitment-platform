package com.groupcc2.recruitment.users.employer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmployerUpgradeRequestBody(
        @NotBlank @Size(max = 255) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Acme Technologies")
        String companyName,
        @NotBlank @Size(max = 10000) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String businessDescription,
        @NotBlank @Email @Size(max = 254) @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String contactEmail,
        @Size(max = 50) String contactPhone,
        @NotBlank @Size(max = 255) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Blantyre")
        String location) {}
