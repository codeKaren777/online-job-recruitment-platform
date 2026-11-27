package com.groupcc2.recruitment.users.service;

import java.util.UUID;

/** Job-module contract invoked when an administrator suspends an employer. */
public interface EmployerJobSuspender {
    void unpublishActiveJobs(UUID employerId);
}
