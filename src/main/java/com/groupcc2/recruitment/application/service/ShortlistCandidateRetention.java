package com.groupcc2.recruitment.application.service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.groupcc2.recruitment.application.entity.Application;
import com.groupcc2.recruitment.application.entity.ShortlistCandidate;
import com.groupcc2.recruitment.application.repository.ShortlistCandidateRepository;
import com.groupcc2.recruitment.profile.dto.DocumentResponse;
import com.groupcc2.recruitment.profile.dto.ProfileResponse;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates immutable recruitment history at the moment an application is shortlisted. */
@Service
public class ShortlistCandidateRetention {
    private final ShortlistCandidateRepository candidates;
    private final UserService users;
    private final ApplicationCvDocuments documents;
    private final ApplicationRls rls;

    public ShortlistCandidateRetention(ShortlistCandidateRepository candidates, UserService users,
                                       ApplicationCvDocuments documents, ApplicationRls rls) {
        this.candidates = candidates;
        this.users = users;
        this.documents = documents;
        this.rls = rls;
    }

    @Transactional
    public ShortlistCandidate retain(Application application, JobSnapshot job, UUID employerId,
                                     UUID decisionAuditId, Instant shortlistedAt) {
        ProfileResponse profile = documents.applicantProfilesForEmployer(Set.of(application.getJobSeekerId()))
                .get(application.getJobSeekerId());
        if (profile == null) {
            profile = new ProfileResponse("Applicant", "Applicant", null,
                    null, null, null, null, null);
        }
        DocumentResponse cv = application.getCvDocumentId() == null ? null
                : documents.metadataForEmployer(Set.of(application.getCvDocumentId()))
                    .get(application.getCvDocumentId());
        ShortlistCandidate candidate = new ShortlistCandidate(application.getId(), decisionAuditId,
                job.id(), employerId, application.getJobSeekerId(), job.title(),
                profile.displayName(), profile.fullName(), profile.dateOfBirth(), profile.phone(),
                profile.location(), profile.biography(), cv == null ? null : cv.id(),
                cv == null ? null : cv.originalFilename(), cv == null ? null : cv.contentType(),
                cv == null ? null : cv.byteSize(), shortlistedAt);
        return candidates.save(candidate);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ShortlistCandidate> list(UUID employerId,
                                                                           org.springframework.data.domain.Pageable pageable) {
        requireEmployer(employerId);
        return candidates.findAllByEmployerId(employerId, pageable);
    }

    @Transactional(readOnly = true)
    public ShortlistCandidate get(UUID employerId, UUID candidateId) {
        requireEmployer(employerId);
        return candidates.findByIdAndEmployerId(candidateId, employerId).orElseThrow(
                () -> new com.groupcc2.recruitment.application.service.ApplicationFailure(
                        com.groupcc2.recruitment.application.service.ApplicationFailure.Code.NOT_FOUND));
    }

    private void requireEmployer(UUID employerId) {
        if (employerId == null) {
            throw new com.groupcc2.recruitment.application.service.ApplicationFailure(
                            com.groupcc2.recruitment.application.service.ApplicationFailure.Code.FORBIDDEN);
        }
        rls.establish(employerId, "");
        User employer = users.findById(employerId);
        users.requireActive(employer);
        if (employer.getRole() != com.groupcc2.recruitment.users.entity.UserRole.EMPLOYER) {
            throw new com.groupcc2.recruitment.application.service.ApplicationFailure(
                            com.groupcc2.recruitment.application.service.ApplicationFailure.Code.FORBIDDEN);
        }
        rls.establish(employerId, employer.getRole().name());
    }
}
