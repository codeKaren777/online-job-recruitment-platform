package com.groupcc2.recruitment.application.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.groupcc2.recruitment.application.entity.Application;
import com.groupcc2.recruitment.application.entity.ApplicationStatus;
import com.groupcc2.recruitment.application.service.JobSnapshot;

/** {@code job} is omitted when the posting is not visible to the caller (see docs/application-module.md). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationResponse(UUID id, UUID jobId, ApplicationStatus status,
                                  Instant submittedAt, Instant updatedAt, Instant shortlistedAt,
                                  Instant rejectedAt, JobSummaryResponse job) {
    public static ApplicationResponse from(Application application, JobSnapshot job) {
        return new ApplicationResponse(application.getId(), application.getJobId(), application.getStatus(),
                application.getSubmittedAt(), application.getUpdatedAt(),
                application.getShortlistedAt(), application.getRejectedAt(),
                job == null ? null : JobSummaryResponse.from(job));
    }
}
