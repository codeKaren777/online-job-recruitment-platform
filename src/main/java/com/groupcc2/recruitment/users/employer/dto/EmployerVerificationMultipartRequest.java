package com.groupcc2.recruitment.users.employer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

/** OpenAPI shape for the two required parts bound by EmployerUpgradeController. */
@Schema(name = "EmployerVerificationMultipartRequest")
public record EmployerVerificationMultipartRequest(
        @Schema(description = "JSON company and contact details.", requiredMode = Schema.RequiredMode.REQUIRED)
        EmployerUpgradeRequestBody application,
        @Schema(description = "Private company registration proof, PDF only, maximum 5 MiB.",
                type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
        MultipartFile document) { }
