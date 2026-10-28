package com.groupcc2.recruitment.job.service;

import com.groupcc2.recruitment.job.dto.JobRequest;
import com.groupcc2.recruitment.job.dto.JobResponse;
import com.groupcc2.recruitment.job.dto.PageResponse;
import com.groupcc2.recruitment.job.entity.Job;
import com.groupcc2.recruitment.job.entity.JobStatus;
import com.groupcc2.recruitment.job.repository.JobRepository;
import com.groupcc2.recruitment.job.repository.JobSpecifications;
import com.groupcc2.recruitment.security.RlsTransactionContext;
import com.groupcc2.recruitment.users.entity.UserRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JobService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final JobRepository jobRepository;
    private final RlsTransactionContext rlsTransactionContext;
    private final CompanyDirectory companies;
    private final Clock clock;

    @Autowired
    public JobService(JobRepository jobRepository, RlsTransactionContext rlsTransactionContext,
                      CompanyDirectory companies) {
        // UTC to match the Postgres container's CURRENT_DATE used by the DB rules.
        this(jobRepository, rlsTransactionContext, companies, Clock.systemUTC());
    }

    JobService(JobRepository jobRepository, RlsTransactionContext rlsTransactionContext,
               CompanyDirectory companies, Clock clock) {
        this.jobRepository = jobRepository;
        this.rlsTransactionContext = rlsTransactionContext;
        this.companies = companies;
        this.clock = clock;
    }

    @Transactional
    public JobResponse createJob(UUID employerId, UserRole role, JobRequest request) {
        establishContext(employerId, role, UserRole.EMPLOYER);
        requireDueDateAfterToday(request.dueDate());
        requireOwnedCompany(request.companyId(), employerId);
        Job job = new Job();
        job.setEmployerId(employerId); // always from the authenticated principal
        job.setCompanyId(request.companyId());
        apply(job, request);
        job.setStatus(JobStatus.ACTIVE);
        return toResponse(jobRepository.save(job));
    }

    @Transactional
    public JobResponse updateJob(UUID employerId, UserRole role, UUID jobId, JobRequest request) {
        establishContext(employerId, role, UserRole.EMPLOYER);
        requireDueDateAfterToday(request.dueDate());
        requireOwnedCompany(request.companyId(), employerId);
        Job job = findOwnedJob(jobId, employerId);
        apply(job, request);
        return toResponse(job);
    }

    /** Closing is idempotent: closing an already-closed job just returns it. */
    @Transactional
    public JobResponse closeJob(UUID employerId, UserRole role, UUID jobId) {
        establishContext(employerId, role, UserRole.EMPLOYER);
        Job job = findOwnedJob(jobId, employerId);
        job.setStatus(JobStatus.CLOSED);
        return toResponse(job);
    }

    @Transactional
    public void deleteJob(UUID employerId, UserRole role, UUID jobId) {
        establishContext(employerId, role, UserRole.EMPLOYER);
        Job job = findOwnedJob(jobId, employerId);
        requireOwnedCompany(job.getCompanyId(), employerId);
        try {
            jobRepository.delete(job);
            jobRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (violatesApplicationForeignKey(exception)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Jobs with submitted applications cannot be deleted", exception);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> getEmployerJobs(
            UUID employerId, UserRole role, JobStatus status, int page, int size) {
        establishContext(employerId, role, UserRole.EMPLOYER);
        Specification<Job> spec = JobSpecifications.ownedBy(employerId)
                .and(JobSpecifications.hasEffectiveStatus(status));
        return PageResponse.from(jobRepository.findAll(spec, pageable(page, size)).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> browseJobs(
            UUID requesterId, UserRole role, String location, String text, int page, int size) {
        establishContext(requesterId, role, UserRole.JOB_SEEKER);
        Specification<Job> spec = JobSpecifications.activeAndNotExpired()
                .and(JobSpecifications.locationContains(location))
                .and(JobSpecifications.textMatches(text));
        return PageResponse.from(jobRepository.findAll(spec, pageable(page, size)).map(this::toResponse));
    }

    /** Employers see only their own jobs; job seekers see only active, non-expired jobs. */
    @Transactional(readOnly = true)
    public JobResponse getJob(UUID jobId, UUID requesterId, UserRole role) {
        if (role != UserRole.EMPLOYER && role != UserRole.JOB_SEEKER) {
            throw forbidden();
        }
        rlsTransactionContext.setAuthenticatedUser(requesterId, role);
        Job job = role == UserRole.EMPLOYER
                ? jobRepository.findByIdAndEmployerId(jobId, requesterId).orElseThrow(JobService::notFound)
                : jobRepository.findPublicById(jobId, JobStatus.ACTIVE).orElseThrow(JobService::notFound);
        return toResponse(job);
    }

    private void establishContext(UUID userId, UserRole role, UserRole requiredRole) {
        if (role != requiredRole) {
            throw forbidden();
        }
        rlsTransactionContext.setAuthenticatedUser(userId, role);
    }

    private Job findOwnedJob(UUID jobId, UUID employerId) {
        return jobRepository.findByIdAndEmployerId(jobId, employerId).orElseThrow(JobService::notFound);
    }

    private void apply(Job job, JobRequest request) {
        job.setCompanyId(request.companyId());
        job.setTitle(request.title().trim());
        job.setDescription(request.description().trim());
        job.setRequiredQualifications(request.requiredQualifications().trim());
        job.setDueDate(request.dueDate());
        job.setLocation(request.location().trim());
    }

    private void requireOwnedCompany(UUID companyId, UUID employerId) {
        if (companyId == null || companies.findOwnedByEmployer(companyId, employerId).isEmpty()) {
            throw notFound();
        }
    }

    private JobResponse toResponse(Job job) {
        boolean pastDue = job.getStatus() == JobStatus.ACTIVE && !job.getDueDate().isAfter(today());
        return JobResponse.from(job, pastDue ? JobStatus.EXPIRED : job.getStatus());
    }

    private void requireDueDateAfterToday(LocalDate dueDate) {
        if (dueDate == null || !dueDate.isAfter(today())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dueDate must be after today");
        }
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), NEWEST_FIRST);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    // 404 (not 403) so we never reveal that someone else's job exists.
    // Swap for the project's common.exception type if one exists.
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient role for job operation");
    }

    private static boolean violatesApplicationForeignKey(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "applications_job_id_fkey".equals(violation.getConstraintName())) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
