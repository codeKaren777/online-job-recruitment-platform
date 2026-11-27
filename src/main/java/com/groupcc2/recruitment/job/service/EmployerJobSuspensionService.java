package com.groupcc2.recruitment.job.service;

import com.groupcc2.recruitment.job.entity.JobStatus;
import com.groupcc2.recruitment.job.repository.JobRepository;
import com.groupcc2.recruitment.users.service.EmployerJobSuspender;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Closes published jobs as part of the administrator's account suspension transaction. */
@Service
public class EmployerJobSuspensionService implements EmployerJobSuspender {
    private final JobRepository jobs;

    public EmployerJobSuspensionService(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Override
    @Transactional
    public void unpublishActiveJobs(UUID employerId) {
        jobs.closeActiveForEmployer(employerId, JobStatus.ACTIVE, JobStatus.CLOSED);
    }
}
