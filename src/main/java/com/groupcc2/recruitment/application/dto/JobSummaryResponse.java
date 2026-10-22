package com.groupcc2.recruitment.application.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.groupcc2.recruitment.application.service.JobSnapshot;

public record JobSummaryResponse(UUID id, String title, String location, LocalDate dueDate, String status) {
    public static JobSummaryResponse from(JobSnapshot job) {
        return new JobSummaryResponse(job.id(), job.title(), job.location(), job.dueDate(), job.state().name());
    }
}
