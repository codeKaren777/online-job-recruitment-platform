package com.groupcc2.recruitment.job.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body for create and update. employerId is deliberately absent: it comes from the JWT. */
public record JobRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 10000) String description,
        @NotBlank @Size(max = 5000) String requiredQualifications,
        @NotNull @Future LocalDate dueDate,
        @NotBlank @Size(max = 255) String location) {
}
