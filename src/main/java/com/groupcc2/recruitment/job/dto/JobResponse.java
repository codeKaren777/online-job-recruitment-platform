package com.groupcc2.recruitment.job.dto;

import com.groupcc2.recruitment.job.entity.Job;
import com.groupcc2.recruitment.job.entity.JobStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JobResponse(
        UUID id,
        UUID employerId,
        String title,
        String description,
        String requiredQualifications,
        LocalDate dueDate,
        String location,
        JobStatus status,
        Instant createdAt,
        Instant updatedAt) {

    /** effectiveStatus reports ACTIVE jobs past their due date as EXPIRED (same rule as the DB view). */
    public static JobResponse from(Job job, JobStatus effectiveStatus) {
        return new JobResponse(
                job.getId(),
                job.getEmployerId(),
                job.getTitle(),
                job.getDescription(),
                job.getRequiredQualifications(),
                job.getDueDate(),
                job.getLocation(),
                effectiveStatus,
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
