package com.groupcc2.recruitment.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.groupcc2.recruitment.job.dto.JobRequest;
import com.groupcc2.recruitment.job.dto.JobResponse;
import com.groupcc2.recruitment.job.entity.Job;
import com.groupcc2.recruitment.job.entity.JobStatus;
import com.groupcc2.recruitment.job.repository.JobRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private JobRepository jobRepository;

    private JobService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new JobService(jobRepository, clock);
    }

    private JobRequest request(LocalDate due) {
        return new JobRequest("Backend Dev", "Build APIs", "BSc IS", due, "Zomba");
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

        JobResponse response = service.createJob(EMPLOYER, request(TODAY.plusDays(3)));

        assertThat(response.employerId()).isEqualTo(EMPLOYER);
        assertThat(response.status()).isEqualTo(JobStatus.ACTIVE);
    }

    @Test
    void createJob_rejectsDueDateOfTodayOrEarlier() {
        assertThatThrownBy(() -> service.createJob(EMPLOYER, request(TODAY)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.createJob(EMPLOYER, request(TODAY.minusDays(1))))
                .isInstanceOf(ResponseStatusException.class);
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void updateJob_ofAnotherEmployer_isNotFound() {
        when(jobRepository.findByIdAndEmployerId(JOB_ID, OTHER_EMPLOYER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateJob(OTHER_EMPLOYER, JOB_ID, request(TODAY.plusDays(2))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void closeJob_ownJob_setsClosed() {
        when(jobRepository.findByIdAndEmployerId(JOB_ID, EMPLOYER))
                .thenReturn(Optional.of(ownedJob(TODAY.plusDays(5))));

        assertThat(service.closeJob(EMPLOYER, JOB_ID).status()).isEqualTo(JobStatus.CLOSED);
    }

    @Test
    void closeJob_ofAnotherEmployer_isNotFound() {
        when(jobRepository.findByIdAndEmployerId(JOB_ID, OTHER_EMPLOYER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.closeJob(OTHER_EMPLOYER, JOB_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getJob_forJobSeeker_usesPublicActiveNonExpiredLookup() {
        when(jobRepository.findPublicById(JOB_ID, JobStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(JOB_ID, SEEKER, false))
                .isInstanceOf(ResponseStatusException.class);
        verify(jobRepository, never()).findByIdAndEmployerId(any(), any());
    }

    @Test
    void getJob_forEmployer_neverFallsBackToOtherEmployersJobs() {
        when(jobRepository.findByIdAndEmployerId(JOB_ID, OTHER_EMPLOYER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(JOB_ID, OTHER_EMPLOYER, true))
                .isInstanceOf(ResponseStatusException.class);
        verify(jobRepository, never()).findPublicById(any(), any());
    }

    @Test
    void getJob_activeJobWhoseDueDateIsToday_isReportedExpired() {
        when(jobRepository.findByIdAndEmployerId(JOB_ID, EMPLOYER))
                .thenReturn(Optional.of(ownedJob(TODAY)));

        assertThat(service.getJob(JOB_ID, EMPLOYER, true).status()).isEqualTo(JobStatus.EXPIRED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void browseJobs_capsPageSize_andUsesDatabasePaging() {
        when(jobRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        service.browseJobs(null, null, -3, 5000);

        verify(jobRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getPageNumber()).isZero();
    }
}
