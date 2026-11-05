package com.groupcc2.recruitment.application.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.groupcc2.recruitment.application.entity.Application;
import com.groupcc2.recruitment.application.entity.ApplicationStatus;
import com.groupcc2.recruitment.application.service.JobSnapshot;
import com.groupcc2.recruitment.profile.dto.DocumentResponse;
import com.groupcc2.recruitment.profile.dto.ProfileResponse;

/** {@code job} is omitted when the posting is not visible to the caller (see docs/application-module.md). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationResponse(UUID id, UUID jobId, UUID applicantId, String applicantDisplayName, ProfileResponse applicantProfile, DocumentResponse cv, ApplicationStatus status,
                                  Instant submittedAt, Instant updatedAt, Instant shortlistedAt,
                                  Instant rejectedAt, JobSummaryResponse job) {
    public ApplicationResponse(UUID id, UUID jobId, UUID applicantId, String applicantDisplayName,
                               DocumentResponse cv, ApplicationStatus status, Instant submittedAt,
                               Instant updatedAt, Instant shortlistedAt, Instant rejectedAt,
                               JobSummaryResponse job) {
        this(id, jobId, applicantId, applicantDisplayName, null, cv, status, submittedAt,
                updatedAt, shortlistedAt, rejectedAt, job);
    }

    public static ApplicationResponse from(Application application, JobSnapshot job) {
        return from(application, job, null, null, null);
    }

    public static ApplicationResponse from(Application application, JobSnapshot job, DocumentResponse cv) {
        return from(application, job, cv, null, null);
    }

    public static ApplicationResponse from(Application application, JobSnapshot job, DocumentResponse cv, String applicantDisplayName) {
        return from(application, job, cv, applicantDisplayName, null);
    }
    public static ApplicationResponse from(Application application, JobSnapshot job, DocumentResponse cv,
                                           String applicantDisplayName, ProfileResponse applicantProfile) {
        return new ApplicationResponse(application.getId(), application.getJobId(), application.getJobSeekerId(), applicantDisplayName,
                applicantProfile, cv, application.getStatus(),
                application.getSubmittedAt(), application.getUpdatedAt(),
                application.getShortlistedAt(), application.getRejectedAt(),
                job == null ? null : JobSummaryResponse.from(job));
    }
}
