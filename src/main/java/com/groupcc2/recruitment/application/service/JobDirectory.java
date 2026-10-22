package com.groupcc2.recruitment.application.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Integration contract owned by the Jobs module. Applications never read the jobs table directly.
 * Until a bean implements this interface, application endpoints answer 503 INTEGRATION_UNAVAILABLE.
 *
 * Implementations should read through recruitment.current_jobs so a posting whose due date has been
 * reached is reported as EXPIRED even before the maintenance function has persisted that status.
 */
public interface JobDirectory {
    Optional<JobSnapshot> findById(UUID jobId);

    /** Returns only the postings that exist and are visible to the caller; missing ids are simply absent. */
    Map<UUID, JobSnapshot> findAllById(Collection<UUID> jobIds);
}
