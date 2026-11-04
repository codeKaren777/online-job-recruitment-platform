package com.groupcc2.recruitment.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.groupcc2.recruitment.application.dto.ApplicationResponse;
import com.groupcc2.recruitment.application.entity.ApplicationDecisionAudit;
import com.groupcc2.recruitment.application.entity.Application;
import com.groupcc2.recruitment.application.entity.ApplicationStatus;
import com.groupcc2.recruitment.application.notification.ApplicationNotificationOutboxRepository;
import com.groupcc2.recruitment.application.repository.ApplicationDecisionAuditRepository;
import com.groupcc2.recruitment.application.repository.ApplicationRepository;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.groupcc2.recruitment.profile.dto.DocumentResponse;
import com.groupcc2.recruitment.profile.dto.ProfileResponse;

/**
 * Job seeker side of applications: FR-08 submit, FR-09 no duplicates, FR-12 track status.
 * Employer review and status changes (FR-10/FR-11) belong to another owner.
 */
@Service
public class ApplicationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApplicationService.class);
    private static final String ONE_PER_SEEKER_CONSTRAINT = "applications_one_per_job_seeker";

    private final ApplicationRepository applications;
    private final UserService users;
    private final ApplicationRls rls;
    private final ObjectProvider<JobDirectory> jobDirectory;
    private final ApplicationDecisionAuditRepository decisionAudits;
    private final ApplicationNotificationOutboxRepository notifications;
    private final Clock clock;
    private ApplicationCvDocuments cvDocuments;

    @Autowired
    void setCvDocuments(ApplicationCvDocuments cvDocuments) { this.cvDocuments = cvDocuments; }

    @Autowired
    public ApplicationService(ApplicationRepository applications, UserService users, ApplicationRls rls,
                              ObjectProvider<JobDirectory> jobDirectory,
                              ApplicationDecisionAuditRepository decisionAudits,
                              ApplicationNotificationOutboxRepository notifications) {
        this(applications, users, rls, jobDirectory, decisionAudits, notifications, Clock.systemUTC());
    }

    public ApplicationService(ApplicationRepository applications, UserService users, ApplicationRls rls,
                       ObjectProvider<JobDirectory> jobDirectory,
                       ApplicationDecisionAuditRepository decisionAudits,
                       ApplicationNotificationOutboxRepository notifications, Clock clock) {
        this.applications = applications;
        this.users = users;
        this.rls = rls;
        this.jobDirectory = jobDirectory;
        this.decisionAudits = decisionAudits;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** FR-08 and FR-09. */
    @Transactional
    public ApplicationResponse apply(UUID userId, UUID jobId) {
        throw new IllegalArgumentException("An application-specific CV is required");
    }

    /** Every newly submitted application receives a separate, validated CV document. */
    @Transactional
    public ApplicationResponse applyWithCv(UUID userId, UUID jobId, MultipartFile cv) {
        if (userId == null || jobId == null || cv == null || cv.isEmpty()) {
            throw new IllegalArgumentException("jobId and a non-empty application CV are required");
        }
        // Check identity/job/duplicate eligibility before touching object storage.
        JobSnapshot job = validateApplication(userId, jobId);
        if (cvDocuments == null) throw new ApplicationFailure(ApplicationFailure.Code.INTEGRATION_UNAVAILABLE);
        DocumentResponse document = cvDocuments.storeForCurrentApplicant(cv);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cvDocuments.removeForCurrentApplicant(document.id());
            throw new ApplicationFailure(ApplicationFailure.Code.INTEGRATION_UNAVAILABLE);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try { cvDocuments.removeForCurrentApplicant(document.id()); }
                    catch (RuntimeException ignored) { log.error("Application CV rollback cleanup failed; reconciliation document {}", document.id()); }
                } else if (status == STATUS_UNKNOWN) {
                    log.error("Application commit outcome unknown; verify submitted CV association for document {}", document.id());
                }
            }
        });
        return persistApplication(userId, jobId, document.id(), job);
    }

    private JobSnapshot validateApplication(UUID userId, UUID jobId) {
        requireJobSeeker(userId);
        JobSnapshot job = directory().findById(jobId)
                .orElseThrow(() -> new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND));
        if (!job.acceptsApplications(LocalDate.now(clock))) throw new ApplicationFailure(ApplicationFailure.Code.JOB_NOT_ACCEPTING_APPLICATIONS);
        if (applications.existsByJobIdAndJobSeekerId(jobId, userId)) throw new ApplicationFailure(ApplicationFailure.Code.DUPLICATE_APPLICATION);
        return job;
    }

    private ApplicationResponse persistApplication(UUID userId, UUID jobId, UUID cvDocumentId, JobSnapshot job) {
        try {
            Application submission = Application.submit(jobId, userId, cvDocumentId);
            Application saved = applications.saveAndFlush(submission);
            notifications.enqueue(saved.getId(), saved.getId(), "SUBMITTED");
            return ApplicationResponse.from(saved, job);
        } catch (DataIntegrityViolationException exception) {
            if (violatesOnePerSeekerConstraint(exception)) {
                // Two concurrent requests both passed the check above; the database let only one win.
                throw new ApplicationFailure(ApplicationFailure.Code.DUPLICATE_APPLICATION);
            }
            throw exception;
        }
    }

    /** FR-12: every application of the caller, newest first, with its posting when visible. */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> listOwn(UUID userId) {
        requireJobSeeker(userId);
        List<Application> mine = applications.findAllByJobSeekerIdOrderBySubmittedAtDesc(userId);
        if (mine.isEmpty()) {
            return List.of();
        }
        Set<UUID> jobIds = mine.stream().map(Application::getJobId).collect(Collectors.toSet());
        Map<UUID, JobSnapshot> jobs = directory().findAllById(jobIds);
        return mine.stream().map(a -> ApplicationResponse.from(a, jobs.get(a.getJobId()))).toList();
    }

    /** FR-12: one application of the caller. Someone else's application id is reported as not found. */
    @Transactional(readOnly = true)
    public ApplicationResponse getOwn(UUID userId, UUID applicationId) {
        requireJobSeeker(userId);
        Application application = applications.findByIdAndJobSeekerId(applicationId, userId)
                .orElseThrow(() -> new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND));
        JobSnapshot job = directory().findAllById(Set.of(application.getJobId())).get(application.getJobId());
        return ApplicationResponse.from(application, job);
    }

    /** Employers may see applications only for their own jobs. */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> listForEmployer(UUID employerId) {
        return listForEmployer(employerId, null, null);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listForEmployer(UUID employerId, UUID jobId, ApplicationStatus status) {
        requireEmployer(employerId);
        Map<UUID, JobSnapshot> jobs;
        if (jobId != null) {
            JobSnapshot job = directory().findOwnedByEmployer(jobId, employerId)
                    .orElseThrow(() -> new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND));
            jobs = Map.of(jobId, job);
        } else {
            jobs = directory().findAllForEmployer(employerId);
        }
        if (jobs.isEmpty()) {
            return List.of();
        }
        List<Application> found = jobId != null
                ? (status == null
                    ? applications.findAllByJobIdOrderBySubmittedAtDesc(jobId)
                    : applications.findAllByJobIdAndStatusOrderBySubmittedAtDesc(jobId, status))
                : (status == null
                    ? applications.findAllByJobIdInOrderBySubmittedAtDesc(jobs.keySet())
                    : applications.findAllByJobIdInAndStatusOrderBySubmittedAtDesc(jobs.keySet(), status));
        Set<UUID> documentIds = found.stream().map(Application::getCvDocumentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> applicantIds = found.stream().map(Application::getJobSeekerId).collect(Collectors.toSet());
        Map<UUID, DocumentResponse> cvMetadata = cvDocuments == null || documentIds.isEmpty()
                ? Map.of() : cvDocuments.metadataForEmployer(documentIds);
        Map<UUID, String> applicantNames = cvDocuments == null || applicantIds.isEmpty()
                ? Map.of() : cvDocuments.applicantNamesForEmployer(applicantIds);
        Map<UUID, ProfileResponse> applicantProfiles = cvDocuments == null || applicantIds.isEmpty()
                ? Map.of() : cvDocuments.applicantProfilesForEmployer(applicantIds);
        return found.stream()
                .map(application -> ApplicationResponse.from(application, jobs.get(application.getJobId()),
                        application.getCvDocumentId() == null ? null : cvMetadata.get(application.getCvDocumentId()),
                        applicantNames.get(application.getJobSeekerId()),
                        applicantProfiles.get(application.getJobSeekerId())))
                .toList();
    }

    @Transactional
    public ApplicationResponse changeStatus(UUID employerId, UUID applicationId, ApplicationStatus newStatus) {
        requireEmployer(employerId);
        if (newStatus != ApplicationStatus.SHORTLISTED && newStatus != ApplicationStatus.REJECTED) {
            throw new ApplicationFailure(ApplicationFailure.Code.INVALID_STATUS_TRANSITION);
        }
        Map<UUID, JobSnapshot> jobs = directory().findAllForEmployer(employerId);
        Application application = applications.findByIdAndJobIdIn(applicationId, jobs.keySet())
                .orElseThrow(() -> new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND));
        ApplicationStatus previousStatus = application.getStatus();
        Instant changedAt = Instant.now(clock);
        try {
            application.changeStatus(newStatus, changedAt);
        } catch (IllegalStateException exception) {
            throw new ApplicationFailure(ApplicationFailure.Code.INVALID_STATUS_TRANSITION);
        }
        ApplicationDecisionAudit audit = decisionAudits.saveAndFlush(new ApplicationDecisionAudit(
                application.getId(), application.getJobId(), employerId, previousStatus, newStatus, changedAt));
        notifications.enqueue(audit.getId(), application.getId(), newStatus.name());
        return employerResponse(application, jobs.get(application.getJobId()));
    }

    /** An employer can read an application only when its job belongs to that employer. */
    @Transactional(readOnly = true)
    public ApplicationResponse getForEmployer(UUID employerId, UUID applicationId) {
        requireEmployer(employerId);
        Map<UUID, JobSnapshot> jobs = directory().findAllForEmployer(employerId);
        if (jobs.isEmpty()) {
            throw new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND);
        }
        Application application = applications.findByIdAndJobIdIn(applicationId, jobs.keySet())
                .orElseThrow(() -> new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND));
        return employerResponse(application, jobs.get(application.getJobId()));
    }

    private ApplicationResponse employerResponse(Application application, JobSnapshot job) {
        UUID cvId = application.getCvDocumentId();
        Map<UUID, DocumentResponse> metadata = cvId == null || cvDocuments == null
                ? Map.of() : cvDocuments.metadataForEmployer(Set.of(cvId));
        Map<UUID, String> names = cvDocuments == null ? Map.of()
                : cvDocuments.applicantNamesForEmployer(Set.of(application.getJobSeekerId()));
        return ApplicationResponse.from(application, job, cvId == null ? null : metadata.get(cvId),
                names.get(application.getJobSeekerId()));
    }

    @Transactional(readOnly = true)
    public com.groupcc2.recruitment.profile.dto.DownloadResponse downloadApplicationCv(UUID employerId, UUID applicationId) {
        requireEmployer(employerId);
        Map<UUID, JobSnapshot> jobs = directory().findAllForEmployer(employerId);
        Application application = applications.findByIdAndJobIdIn(applicationId, jobs.keySet())
                .orElseThrow(() -> new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND));
        if (application.getCvDocumentId() == null || cvDocuments == null)
            throw new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND);
        return cvDocuments.downloadForEmployer(application.getJobId(), application.getJobSeekerId(), application.getCvDocumentId());
    }

    @Transactional(readOnly = true)
    public ProfileResponse getApplicantProfile(UUID employerId, UUID applicationId) {
        requireEmployer(employerId);
        Map<UUID, JobSnapshot> jobs = directory().findAllForEmployer(employerId);
        Application application = applications.findByIdAndJobIdIn(applicationId, jobs.keySet())
                .orElseThrow(() -> new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND));
        if (cvDocuments == null) {
            throw new ApplicationFailure(ApplicationFailure.Code.INTEGRATION_UNAVAILABLE);
        }
        Map<UUID, ProfileResponse> profiles =
                cvDocuments.applicantProfilesForEmployer(Set.of(application.getJobSeekerId()));
        ProfileResponse profile = profiles.get(application.getJobSeekerId());
        if (profile == null) {
            throw new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND);
        }
        return profile;
    }

    /**
     * Verifies the relationship required before an employer can read an applicant's profile.
     * The job ownership check and application lookup are both scoped to the supplied identifiers.
     */
    public void requireSubmittedApplication(UUID employerId, UUID jobId, UUID applicantId) {
        requireEmployer(employerId);
        if (directory().findOwnedByEmployer(jobId, employerId).isEmpty()
                || !applications.existsByJobIdAndJobSeekerId(jobId, applicantId)) {
            throw new ApplicationFailure(ApplicationFailure.Code.FORBIDDEN);
        }
    }

    /**
     * Mirrors ProfileService: bootstrap with self-only visibility, then derive the role from the persisted
     * active account (never from token claims or request data) and establish it for RLS.
     */
    private void requireJobSeeker(UUID userId) {
        requireRole(userId, UserRole.JOB_SEEKER);
    }

    private void requireEmployer(UUID userId) {
        requireRole(userId, UserRole.EMPLOYER);
    }

    private void requireRole(UUID userId, UserRole requiredRole) {
        if (userId == null) {
            throw new ApplicationFailure(ApplicationFailure.Code.FORBIDDEN);
        }
        rls.establish(userId, "");
        User user = users.findById(userId);
        users.requireActive(user);
        if (user.getRole() != requiredRole) {
            throw new ApplicationFailure(ApplicationFailure.Code.FORBIDDEN);
        }
        rls.establish(userId, user.getRole().name());
    }

    private JobDirectory directory() {
        JobDirectory directory = jobDirectory.getIfAvailable();
        if (directory == null) {
            throw new ApplicationFailure(ApplicationFailure.Code.INTEGRATION_UNAVAILABLE);
        }
        return directory;
    }

    private static boolean violatesOnePerSeekerConstraint(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(ONE_PER_SEEKER_CONSTRAINT)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
