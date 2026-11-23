package com.groupcc2.recruitment.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "ApiError", description = "Safe, consistent error response returned by the API.")
public record ApiErrorResponse(
        @Schema(example = "2026-11-23T14:35:20Z") Instant timestamp,
        @Schema(example = "404") int status,
        @Schema(example = "RESOURCE_NOT_FOUND") String code,
        @Schema(example = "The requested resource was not found.") String message,
        @Schema(example = "/api/jobs/123") String path,
        @Schema(example = "7f8c2a91-6a62-4c68-9bd5-279b68ac4e37") String correlationId,
        List<FieldError> fieldErrors) {

    @Schema(name = "ApiFieldError")
    public record FieldError(
            @Schema(example = "email") String field,
            @Schema(example = "must be a valid email address") String message) {}
}
