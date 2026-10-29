package com.groupcc2.recruitment.application.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

/** The applicant is never part of the request; it always comes from the authenticated token. */
@Schema(name = "ApplyRequest", description = "Submit an application to an eligible job posting.")
public record ApplyRequest(
        @NotNull(message = "A job ID is required.")
        @Schema(description = "ID of the job posting to apply to.", type = "string", format = "uuid",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "7e2778ac-26fd-4a2d-b318-8e06e315a082")
        UUID jobId) {
}
