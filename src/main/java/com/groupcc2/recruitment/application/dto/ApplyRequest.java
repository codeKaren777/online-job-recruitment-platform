package com.groupcc2.recruitment.application.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

/** The applicant is never part of the request; it always comes from the authenticated token. */
public record ApplyRequest(
        @NotNull
        @Schema(description = "ID of the job to apply for", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "11111111-1111-1111-1111-111111111111")
        UUID jobId) {
}
