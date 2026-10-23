package com.groupcc2.recruitment.application.service;

import com.groupcc2.recruitment.profile.service.EmployerApplicationAccess;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Supplies the profile module with the application's specific-job authorization contract. */
@Component
class EmployerApplicationAccessAdapter implements EmployerApplicationAccess {
    private final ApplicationService applications;

    EmployerApplicationAccessAdapter(ApplicationService applications) {
        this.applications = applications;
    }

    @Override
    public void requireSubmittedApplication(UUID employerId, UUID jobId, UUID applicantId) {
        applications.requireSubmittedApplication(employerId, jobId, applicantId);
    }
}
