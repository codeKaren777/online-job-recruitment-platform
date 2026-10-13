package com.groupcc2.recruitment.profile.service;
import java.util.UUID;
/** Application owner must verify a persisted application to this employer's specific job.
 * Implement through Jobs/Applications service contracts, never client role/owner fields. */
public interface EmployerApplicationAccess {
 void requireSubmittedApplication(UUID employerId, UUID jobId, UUID applicantId);
}
