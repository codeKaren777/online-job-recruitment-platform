package com.groupcc2.recruitment.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.groupcc2.recruitment.job.dto.JobRequest;
import com.groupcc2.recruitment.job.dto.JobResponse;
import com.groupcc2.recruitment.job.entity.Job;
import com.groupcc2.recruitment.job.entity.JobStatus;
import com.groupcc2.recruitment.job.repository.JobRepository;
import com.groupcc2.recruitment.security.RlsTransactionContext;
import com.groupcc2.recruitment.users.entity.UserRole;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final UUID EMPLOYER = UUID.randomUUID();
    private static final UUID OTHER_EMPLOYER = UUID.randomUUID();
    private static final UUID SEEKER = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    @Mock
    private JobRepository jobRepository;

    @Mock
    private RlsTransactionContext rlsTransactionContext;

    @Mock
    private CompanyDirectory companyDirectory;

    private JobService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new JobService(jobRepository, rlsTransactionContext, companyDirectory, clock);
        when(companyDirectory.findOwnedByEmployer(COMPANY_ID, EMPLOYER))
                .thenReturn(Optional.of(new CompanyReference(COMPANY_ID, EMPLOYER)));
    }

    private JobRequest request(LocalDate due) {
        return new JobRequest(COMPANY_ID, "Backend Dev", "Build APIs", "BSc IS", due, "Zomba");
    }

    private Job ownedJob(LocalDate due) {
        Job job = new Job();
        job.setEmployerId(EMPLOYER);
        job.setStatus(JobStatus.ACTIVE);
        job.setTitle("t");
        job.setDescription("d");
        job.setRequiredQualifications("q");
        job.setLocation("l");
        job.setDueDate(due);
        return job;
    }

    @Test
    void createJob_usesEmployerFromPrincipal_andStartsActive() {
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
        when(companyDirectory.findOwnedByEmployer(COMPANY_ID, EMPLOYER))
                .thenReturn(Optional.of(new CompanyReference(COMPANY_ID, EMPLOYER)));

        JobResponse response = service.createJob(EMPLOYER, UserRole.EMPLOYER, request(TODAY.plusDays(3)));

        assertThat(response.employerId()).isEqualTo(EMPLOYER);
        assertThat(response.status()).isEqualTo(JobStatus.ACTIVE);
        InOrder order = inOrder(rlsTransactionContext, jobRepository);
        order.verify(rlsTransactionContext).setAuthenticatedUser(EMPLOYER, UserRole.EMPLOYER);
        order.verify(jobRepository).save(any(Job.class));
    }

    @Test
    void createJob_rejectsDueDateOfTodayOrEarlier() {
        when(companyDirectory.findOwnedByEmployer(COMPANY_ID, EMPLOYER))
                .thenReturn(Optional.of(new CompanyReference(COMPANY_ID, EMPLOYER)));
        assertThatThrownBy(() -> service.createJob(EMPLOYER, UserRole.EMPLOYER, request(TODAY)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.createJob(EMPLOYER, UserRole.EMPLOYER, request(TODAY.minusDays(1))))
                .isInstanceOf(ResponseStatusException.class);
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void employerOperation_rejectsJobSeekerBeforeAccessingProtectedData() {
        assertThatThrownBy(() -> service.createJob(SEEKER, UserRole.JOB_SEEKER, request(TODAY.plusDays(2))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(rlsTransactionContext, never()).setAuthenticatedUser(any(), any());
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void createJob_rejectsCompanyOwnedByAnotherEmployer() {
        when(companyDirectory.findOwnedByEmployer(COMPANY_ID, EMPLOYER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createJob(EMPLOYER, UserRole.EMPLOYER,
                request(TODAY.plusDays(2))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void updateJob_ofAnotherEmployer_isNotFound() {
        when(jobRepository.findByIdAndEmployerId(JOB_ID, OTHER_EMPLOYER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateJob(
                OTHER_EMPLOYER, UserRole.EMPLOYER, JOB_ID, request(TODAY.plusDays(2))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        InOrder order = inOrder(rlsTransactionContext, jobRepository);
        order.verify(rlsTransactionContext).setAuthenticatedUser(OTHER_EMPLOYER, UserRole.EMPLOYER);
        order.verify(jobRepository).findByIdAndEmployerId(JOB_ID, OTHER_EMPLOYER);
    }

    @Test
    void closeJob_ownJob_setsClosed() {
        when(jobRepository.findByIdAndEmployerId(JOB_ID, EMPLOYER))
                .thenReturn(Optional.of(ownedJob(TODAY.plusDays(5))));

        assertThat(service.closeJob(EMPLOYER, UserRole.EMPLOYER, JOB_ID).status()).isEqualTo(JobStatus.CLOSED);
        InOrder order = inOrder(rlsTransactionContext, jobRepository);
        order.verify(rlsTransactionContext).setAuthenticatedUser(EMPLOYER, UserRole.EMPLOYER);
        order.verify(jobRepository).findByIdAndEmployerId(JOB_ID, EMPLOYER);
    }

    @Test
    void closeJob_ofAnotherEmployer_isNotFound() {
        when(jobRepository.findByIdAndEmployerId(JOB_ID, OTHER_EMPLOYER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.closeJob(OTHER_EMPLOYER, UserRole.EMPLOYER, JOB_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getJob_forJobSeeker_usesPublicActiveNonExpiredLookup() {
        when(jobRepository.findPublicById(JOB_ID, JobStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(JOB_ID, SEEKER, UserRole.JOB_SEEKER))
                .isInstanceOf(ResponseStatusException.class);
        InOrder order = inOrder(rlsTransactionContext, jobRepository);
        order.verify(rlsTransactionContext).setAuthenticatedUser(SEEKER, UserRole.JOB_SEEKER);
        order.verify(jobRepository).findPublicById(JOB_ID, JobStatus.ACTIVE);
        verify(jobRepository, never()).findByIdAndEmployerId(any(), any());
    }

    @Test
    void getJob_forEmployer_neverFallsBackToOtherEmployersJobs() {
        when(jobRepository.findByIdAndEmployerId(JOB_ID, OTHER_EMPLOYER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(JOB_ID, OTHER_EMPLOYER, UserRole.EMPLOYER))
                .isInstanceOf(ResponseStatusException.class);
        verify(rlsTransactionContext).setAuthenticatedUser(OTHER_EMPLOYER, UserRole.EMPLOYER);
        verify(jobRepository, never()).findPublicById(any(), any());
    }

    @Test
    void getJob_activeJobWhoseDueDateIsToday_isReportedExpired() {
        when(jobRepository.findByIdAndEmployerId(JOB_ID, EMPLOYER))
                .thenReturn(Optional.of(ownedJob(TODAY)));

        assertThat(service.getJob(JOB_ID, EMPLOYER, UserRole.EMPLOYER).status()).isEqualTo(JobStatus.EXPIRED);
        verify(jobRepository).findByIdAndEmployerId(JOB_ID, EMPLOYER);
        verify(jobRepository, never()).findPublicById(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void browseJobs_capsPageSize_andUsesDatabasePaging() {
        when(jobRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        service.browseJobs(SEEKER, UserRole.JOB_SEEKER, null, null, -3, 5000);

        verify(jobRepository).findAll(any(Specification.class), captor.capture());
        InOrder order = inOrder(rlsTransactionContext, jobRepository);
        order.verify(rlsTransactionContext).setAuthenticatedUser(SEEKER, UserRole.JOB_SEEKER);
        order.verify(jobRepository).findAll(any(Specification.class), any(Pageable.class));
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getPageNumber()).isZero();
    }

    @Test
    void browseJobs_rejectsEmployerRole() {
        assertThatThrownBy(() -> service.browseJobs(EMPLOYER, UserRole.EMPLOYER, null, null, 0, 20))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(rlsTransactionContext, never()).setAuthenticatedUser(any(), any());
        verify(jobRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getEmployerJobs_setsEmployerContextBeforeQuery() {
        when(jobRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.getEmployerJobs(EMPLOYER, UserRole.EMPLOYER, null, 0, 20);

        InOrder order = inOrder(rlsTransactionContext, jobRepository);
        order.verify(rlsTransactionContext).setAuthenticatedUser(EMPLOYER, UserRole.EMPLOYER);
        order.verify(jobRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void employerJobListing_rejectsJobSeekerRole() {
        assertThatThrownBy(() -> service.getEmployerJobs(SEEKER, UserRole.JOB_SEEKER, null, 0, 20))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(rlsTransactionContext, never()).setAuthenticatedUser(any(), any());
        verify(jobRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }
}
