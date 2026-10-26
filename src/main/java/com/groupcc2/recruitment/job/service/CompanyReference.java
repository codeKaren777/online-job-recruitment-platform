package com.groupcc2.recruitment.job.service;

import java.util.UUID;

/** Read-only company ownership data supplied by the Company module. */
public record CompanyReference(UUID id, UUID employerId) {
}
