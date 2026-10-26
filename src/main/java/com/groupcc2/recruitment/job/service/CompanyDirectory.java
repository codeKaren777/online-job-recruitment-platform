package com.groupcc2.recruitment.job.service;

import java.util.Optional;
import java.util.UUID;

/** Cross-module contract used by Jobs to validate company ownership. */
public interface CompanyDirectory {
    Optional<CompanyReference> findOwnedByEmployer(UUID companyId, UUID employerId);
}
