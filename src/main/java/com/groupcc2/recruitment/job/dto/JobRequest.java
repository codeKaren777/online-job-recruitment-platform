package com.groupcc2.recruitment.job.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** Body for create and update. employerId is deliberately absent: it comes from the JWT. */
public record JobRequest(
        @NotBlank @Size(max = 255)
        @Schema(description = "Job title", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Senior Java Developer")
        String title,
        @NotBlank @Size(max = 10000)
        @Schema(description = "Full job description", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Build and maintain recruitment platform services.")
        String description,
        @NotBlank @Size(max = 5000)
        @Schema(description = "Required qualifications", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Java 21, Spring Boot, PostgreSQL")
        String requiredQualifications,
        @NotNull @Future
        @Schema(description = "Date after which applications are no longer accepted",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "2099-12-31", format = "date")
        LocalDate dueDate,
        @NotBlank @Size(max = 255)
        @Schema(description = "Job location", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Blantyre")
        String location) {
}
