package com.groupcc2.recruitment.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.groupcc2.recruitment.application.entity.ShortlistCandidate;
import com.groupcc2.recruitment.profile.dto.DocumentResponse;
import com.groupcc2.recruitment.profile.dto.ProfileResponse;

public record ShortlistCandidateResponse(
        UUID id,
        UUID applicationId,
        UUID jobId,
        String jobTitle,
        UUID jobSeekerId,
        ProfileResponse applicantProfile,
        DocumentResponse cv,
        Instant shortlistedAt,
        Instant retainedAt) {

    public static ShortlistCandidateResponse from(ShortlistCandidate candidate) {
        ProfileResponse profile = new ProfileResponse(candidate.getDisplayName(), candidate.getFullName(),
                candidate.getDateOfBirth(), candidate.getPhone(), candidate.getLocation(),
                candidate.getBiography(), null, null);
        DocumentResponse cv = candidate.getCvDocumentId() == null ? null : new DocumentResponse(
                candidate.getCvDocumentId(), candidate.getCvFilename(), candidate.getCvContentType(),
                candidate.getCvByteSize(), null);
        return new ShortlistCandidateResponse(candidate.getId(), candidate.getApplicationId(),
                candidate.getJobId(), candidate.getJobTitle(), candidate.getJobSeekerId(),
                profile, cv, candidate.getShortlistedAt(),
                candidate.getRetainedAt());
    }
}
