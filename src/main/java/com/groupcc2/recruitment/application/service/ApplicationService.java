package com.groupcc2.recruitment.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.groupcc2.recruitment.application.dto.ApplicationResponse;
import com.groupcc2.recruitment.application.entity.Application;
import com.groupcc2.recruitment.application.repository.ApplicationRepository;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job seeker side of applications: FR-08 submit, FR-09 no duplicates, FR-12 track status.
 * Employer review and status changes (FR-10/FR-11) belong to another owner.
 */
@Service
public class ApplicationService {
    private static final String ONE_PER_SEEKER_CONSTRAINT = "applications_one_per_job_seeker";

    private final ApplicationRepository applications;
    private final UserService users;
    private final ApplicationRls rls;
    private final ObjectProvider<JobDirectory> jobDirectory;
    private final Clock clock;

    @Autowired
    public ApplicationService(ApplicationRepository applications, UserService users, ApplicationRls rls,
                              ObjectProvider<JobDirectory> jobDirectory) {
        this(applications, users, rls, jobDirectory, Clock.systemUTC());
    }

    public ApplicationService(ApplicationRepository applications, UserService users, ApplicationRls rls,
                       ObjectProvider<JobDirectory> jobDirectory, Clock clock) {
        this.applications = applications;
        this.users = users;
        this.rls = rls;
        this.jobDirectory = jobDirectory;
        this.clock = clock;
    }

    /** FR-08 and FR-09. */
    @Transactional
    public ApplicationResponse apply(UUID userId, UUID jobId) {
        if (userId == null || jobId == null) {
            throw new IllegalArgumentException("userId and jobId are required");
        }
        requireJobSeeker(userId);
        JobSnapshot job = directory().findById(jobId)
                .orElseThrow(() -> new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND));
        if (!job.acceptsApplications(LocalDate.now(clock))) {
            throw new ApplicationFailure(ApplicationFailure.Code.JOB_NOT_ACCEPTING_APPLICATIONS);
        }
        // Friendly check first; the UNIQUE (job_id, job_seeker_id) constraint is the real guarantee.
        if (applications.existsByJobIdAndJobSeekerId(jobId, userId)) {
            throw new ApplicationFailure(ApplicationFailure.Code.DUPLICATE_APPLICATION);
        }
        try {
            Application saved = applications.saveAndFlush(Application.submit(jobId, userId));
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

    /**
     * Mirrors ProfileService: bootstrap with self-only visibility, then derive the role from the persisted
     * active account (never from token claims or request data) and establish it for RLS.
     */
    private void requireJobSeeker(UUID userId) {
        if (userId == null) {
            throw new ApplicationFailure(ApplicationFailure.Code.FORBIDDEN);
        }
        rls.establish(userId, "");
        User user = users.findById(userId);
        users.requireActive(user);
        if (user.getRole() != UserRole.JOB_SEEKER) {
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
