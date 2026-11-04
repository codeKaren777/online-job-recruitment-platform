package com.groupcc2.recruitment.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.groupcc2.recruitment.application.entity.Application;
import com.groupcc2.recruitment.application.entity.ApplicationStatus;
import com.groupcc2.recruitment.application.dto.ApplicationResponse;
import com.groupcc2.recruitment.application.repository.ApplicationRepository;
import com.groupcc2.recruitment.application.repository.ApplicationDecisionAuditRepository;
import com.groupcc2.recruitment.application.service.ApplicationFailure;
import com.groupcc2.recruitment.application.notification.ApplicationNotificationOutboxRepository;
import com.groupcc2.recruitment.application.service.ApplicationRls;
import com.groupcc2.recruitment.application.service.ApplicationService;
import com.groupcc2.recruitment.application.service.ApplicationCvDocuments;
import com.groupcc2.recruitment.profile.dto.DocumentResponse;
import com.groupcc2.recruitment.application.service.JobDirectory;
import com.groupcc2.recruitment.application.service.JobSnapshot;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.InactiveAccountException;
import com.groupcc2.recruitment.users.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Mock ApplicationRepository repository;
    @Mock UserService users;
    @Mock ApplicationRls rls;
    @Mock ApplicationDecisionAuditRepository decisionAudits;
    @Mock ApplicationNotificationOutboxRepository notifications;
    @Mock ObjectProvider<JobDirectory> directoryProvider;
    @Mock JobDirectory directory;
    @Mock ApplicationCvDocuments cvDocuments;

    ApplicationService service;
    User seeker = new User("seeker@example.com", "hash", UserRole.JOB_SEEKER);
    UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new ApplicationService(repository, users, rls, directoryProvider,
                decisionAudits, notifications, clock);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "cvDocuments", cvDocuments);
        when(directoryProvider.getIfAvailable()).thenReturn(directory);
        when(users.findById(seeker.getId())).thenReturn(seeker);
        when(repository.saveAndFlush(any(Application.class))).thenAnswer(call -> call.getArgument(0));
        when(decisionAudits.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private JobSnapshot job(LocalDate due, JobSnapshot.State state) {
        return new JobSnapshot(jobId, "Backend Developer", "Zomba", due, state);
    }

    private ApplicationResponse submit(UUID applicantId, UUID targetJobId) {
        UUID cvId = UUID.randomUUID();
        when(cvDocuments.storeForCurrentApplicant(any())).thenReturn(
                new DocumentResponse(cvId, "application.pdf", "application/pdf", 20L, Instant.now()));
        var cv = new org.springframework.mock.web.MockMultipartFile("cv", "application.pdf", "application/pdf", "%PDF-1.7".getBytes());
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try { return service.applyWithCv(applicantId, targetJobId, cv); }
        finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test
    void legacyNoCvSubmissionMethodRejectsRequest() {
        assertThatThrownBy(() -> service.apply(seeker.getId(), jobId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CV is required");
        verifyNoInteractions(repository, notifications, cvDocuments);
    }

    @Test
    void submitsPendingApplicationToActiveJob() {
        when(directory.findById(jobId)).thenReturn(Optional.of(job(TODAY.plusDays(3), JobSnapshot.State.ACTIVE)));

        var response = submit(seeker.getId(), jobId);
        var savedApplication = org.mockito.ArgumentCaptor.forClass(Application.class);

        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(response.jobId()).isEqualTo(jobId);
        assertThat(response.job().title()).isEqualTo("Backend Developer");
        verify(repository).saveAndFlush(savedApplication.capture());
        assertThat(savedApplication.getValue().getJobSeekerId()).isEqualTo(seeker.getId());
        assertThat(savedApplication.getValue().getStatus()).isEqualTo(ApplicationStatus.PENDING);
        verify(notifications).enqueue(response.id(), response.id(), "SUBMITTED");
        verify(rls).establish(seeker.getId(), "JOB_SEEKER");
    }

    @Test
    void submitsEachApplicationWithItsOwnUploadedCv() {
        when(directory.findById(jobId)).thenReturn(Optional.of(job(TODAY.plusDays(3), JobSnapshot.State.ACTIVE)));
        UUID cvId = UUID.randomUUID();
        when(cvDocuments.storeForCurrentApplicant(any())).thenReturn(
                new DocumentResponse(cvId, "role-cv.pdf", "application/pdf", 42, Instant.now()));
        org.springframework.mock.web.MockMultipartFile cv = new org.springframework.mock.web.MockMultipartFile(
                "cv", "role-cv.pdf", "application/pdf", "%PDF-1.7".getBytes());
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            ApplicationResponse response = service.applyWithCv(seeker.getId(), jobId, cv);
            var saved = org.mockito.ArgumentCaptor.forClass(Application.class);
            verify(repository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getCvDocumentId()).isEqualTo(cvId);
            assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsSecondApplicationToSameJob() {
        when(directory.findById(jobId)).thenReturn(Optional.of(job(TODAY.plusDays(3), JobSnapshot.State.ACTIVE)));
        when(repository.existsByJobIdAndJobSeekerId(jobId, seeker.getId())).thenReturn(true);

        assertThatThrownBy(() -> submit(seeker.getId(), jobId))
                .isInstanceOfSatisfying(ApplicationFailure.class,
                        e -> assertThat(e.code()).isEqualTo(ApplicationFailure.Code.DUPLICATE_APPLICATION));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void mapsConcurrentDuplicateToDuplicateApplication() {
        when(directory.findById(jobId)).thenReturn(Optional.of(job(TODAY.plusDays(3), JobSnapshot.State.ACTIVE)));
        when(repository.saveAndFlush(any(Application.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("violates unique constraint \"applications_one_per_job_seeker\"")));

        assertThatThrownBy(() -> submit(seeker.getId(), jobId))
                .isInstanceOfSatisfying(ApplicationFailure.class,
                        e -> assertThat(e.code()).isEqualTo(ApplicationFailure.Code.DUPLICATE_APPLICATION));
    }

    @Test
    void doesNotHideOtherIntegrityViolationsAsDuplicates() {
        when(directory.findById(jobId)).thenReturn(Optional.of(job(TODAY.plusDays(3), JobSnapshot.State.ACTIVE)));
        when(repository.saveAndFlush(any(Application.class)))
                .thenThrow(new DataIntegrityViolationException("foreign key violation"));

        assertThatThrownBy(() -> submit(seeker.getId(), jobId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsApplicationOnDueDateAndAfter() {
        for (LocalDate due : new LocalDate[] {TODAY, TODAY.minusDays(1)}) {
            when(directory.findById(jobId)).thenReturn(Optional.of(job(due, JobSnapshot.State.ACTIVE)));
            assertThatThrownBy(() -> submit(seeker.getId(), jobId))
                    .isInstanceOfSatisfying(ApplicationFailure.class, e -> assertThat(e.code())
                            .isEqualTo(ApplicationFailure.Code.JOB_NOT_ACCEPTING_APPLICATIONS));
        }
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsExpiredOrClosedStatusEvenBeforeDueDate() {
        for (var state : new JobSnapshot.State[] {JobSnapshot.State.EXPIRED, JobSnapshot.State.CLOSED}) {
            when(directory.findById(jobId)).thenReturn(Optional.of(job(TODAY.plusDays(5), state)));
            assertThatThrownBy(() -> submit(seeker.getId(), jobId))
                    .isInstanceOfSatisfying(ApplicationFailure.class, e -> assertThat(e.code())
                            .isEqualTo(ApplicationFailure.Code.JOB_NOT_ACCEPTING_APPLICATIONS));
        }
    }

    @Test
    void unknownJobIsNotFound() {
        when(directory.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submit(seeker.getId(), jobId))
                .isInstanceOfSatisfying(ApplicationFailure.class,
                        e -> assertThat(e.code()).isEqualTo(ApplicationFailure.Code.NOT_FOUND));
    }

    @Test
    void employersAndAdminsCannotApply() {
        for (UserRole role : new UserRole[] {UserRole.EMPLOYER, UserRole.ADMIN}) {
            User other = new User(role + "@example.com", "hash", role);
            when(users.findById(other.getId())).thenReturn(other);
                    assertThatThrownBy(() -> submit(other.getId(), jobId))
                    .isInstanceOfSatisfying(ApplicationFailure.class,
                            e -> assertThat(e.code()).isEqualTo(ApplicationFailure.Code.FORBIDDEN));
        }
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveAccountCannotApply() {
        doThrow(new InactiveAccountException()).when(users).requireActive(seeker);

        assertThatThrownBy(() -> submit(seeker.getId(), jobId)).isInstanceOf(InactiveAccountException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reportsIntegrationUnavailableWhenJobsModuleIsMissing() {
        when(directoryProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> submit(seeker.getId(), jobId))
                .isInstanceOfSatisfying(ApplicationFailure.class,
                        e -> assertThat(e.code()).isEqualTo(ApplicationFailure.Code.INTEGRATION_UNAVAILABLE));
    }

    @Test
    void requiresEmployerOwnedJobAndSubmittedApplicant() {
        User employer = new User("employer@example.com", "hash", UserRole.EMPLOYER);
        UUID applicantId = seeker.getId();
        when(users.findById(employer.getId())).thenReturn(employer);
        when(directory.findOwnedByEmployer(jobId, employer.getId()))
                .thenReturn(Optional.of(job(TODAY.plusDays(3), JobSnapshot.State.ACTIVE)));
        when(repository.existsByJobIdAndJobSeekerId(jobId, applicantId)).thenReturn(true);

        service.requireSubmittedApplication(employer.getId(), jobId, applicantId);

        verify(rls).establish(employer.getId(), "EMPLOYER");
        verify(repository).existsByJobIdAndJobSeekerId(jobId, applicantId);
    }

    @Test
    void rejectsApplicantWithoutSubmissionOrEmployerOwnedJob() {
        User employer = new User("employer@example.com", "hash", UserRole.EMPLOYER);
        when(users.findById(employer.getId())).thenReturn(employer);

        assertThatThrownBy(() -> service.requireSubmittedApplication(employer.getId(), jobId, seeker.getId()))
                .isInstanceOfSatisfying(ApplicationFailure.class,
                        e -> assertThat(e.code()).isEqualTo(ApplicationFailure.Code.FORBIDDEN));
    }

    @Test
    void listsOwnApplicationsWithJobAndToleratesInvisibleJobs() {
        UUID visibleJob = UUID.randomUUID();
        UUID hiddenJob = UUID.randomUUID();
        Application first = Application.submit(visibleJob, seeker.getId());
        Application second = Application.submit(hiddenJob, seeker.getId());
        when(repository.findAllByJobSeekerIdOrderBySubmittedAtDesc(seeker.getId())).thenReturn(java.util.List.of(first, second));
        when(directory.findAllById(Set.of(visibleJob, hiddenJob))).thenReturn(Map.of(visibleJob,
                new JobSnapshot(visibleJob, "Analyst", null, TODAY.plusDays(1), JobSnapshot.State.ACTIVE)));

        var result = service.listOwn(seeker.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).job().title()).isEqualTo("Analyst");
        assertThat(result.get(1).job()).isNull();
    }

    @Test
    void emptyListDoesNotQueryJobs() {
        when(repository.findAllByJobSeekerIdOrderBySubmittedAtDesc(seeker.getId())).thenReturn(java.util.List.of());

        assertThat(service.listOwn(seeker.getId())).isEmpty();
        verify(directory, never()).findAllById(any());
    }

    @Test
    void otherSeekersApplicationIsNotFound() {
        UUID applicationId = UUID.randomUUID();
        when(repository.findByIdAndJobSeekerId(applicationId, seeker.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwn(seeker.getId(), applicationId))
                .isInstanceOfSatisfying(ApplicationFailure.class,
                        e -> assertThat(e.code()).isEqualTo(ApplicationFailure.Code.NOT_FOUND));
    }

    @Test
    void employerListsOnlyApplicationsForOwnedJobs() {
        User employer = new User("employer@example.com", "hash", UserRole.EMPLOYER);
        UUID ownedJobId = UUID.randomUUID();
        UUID foreignJobId = UUID.randomUUID();
        Application owned = Application.submit(ownedJobId, seeker.getId());
        Application foreign = Application.submit(foreignJobId, seeker.getId());
        when(users.findById(employer.getId())).thenReturn(employer);
        when(directory.findAllForEmployer(employer.getId())).thenReturn(Map.of(ownedJobId,
                new JobSnapshot(ownedJobId, "Owned", "Zomba", TODAY.plusDays(1), JobSnapshot.State.ACTIVE)));
        when(repository.findAllByJobIdInOrderBySubmittedAtDesc(Set.of(ownedJobId)))
                .thenReturn(List.of(owned));

        var result = service.listForEmployer(employer.getId());

        assertThat(result).extracting(ApplicationResponse::jobId).containsExactly(ownedJobId);
        verify(rls).establish(employer.getId(), "EMPLOYER");
    }

    @Test
    void employerListingReturnsTheCvAssociatedWithThatApplication() {
        User employer = new User("employer@example.com", "hash", UserRole.EMPLOYER);
        UUID applicant = seeker.getId();
        UUID cvId = UUID.randomUUID();
        Application application = Application.submit(jobId, applicant, cvId);
        DocumentResponse cv = new DocumentResponse(cvId, "developer.pdf", "application/pdf", 120L, Instant.now());
        when(users.findById(employer.getId())).thenReturn(employer);
        when(directory.findAllForEmployer(employer.getId())).thenReturn(Map.of(jobId,
                job(TODAY.plusDays(2), JobSnapshot.State.ACTIVE)));
        when(repository.findAllByJobIdInOrderBySubmittedAtDesc(Set.of(jobId))).thenReturn(List.of(application));
        when(cvDocuments.metadataForEmployer(Set.of(cvId))).thenReturn(Map.of(cvId, cv));
        when(cvDocuments.applicantNamesForEmployer(Set.of(applicant))).thenReturn(Map.of(applicant, "Applicant Name"));

        ApplicationResponse response = service.listForEmployer(employer.getId()).getFirst();

        assertThat(response.cv()).isEqualTo(cv);
        assertThat(response.applicantDisplayName()).isEqualTo("Applicant Name");
        assertThat(response.applicantId()).isEqualTo(applicant);
    }

    @Test
    void employerCannotReadAnApplicationOutsideOwnedJobs() {
        User employer = new User("employer@example.com", "hash", UserRole.EMPLOYER);
        UUID applicationId = UUID.randomUUID();
        when(users.findById(employer.getId())).thenReturn(employer);
        when(directory.findAllForEmployer(employer.getId())).thenReturn(Map.of());

        assertThatThrownBy(() -> service.getForEmployer(employer.getId(), applicationId))
                .isInstanceOfSatisfying(ApplicationFailure.class,
                        e -> assertThat(e.code()).isEqualTo(ApplicationFailure.Code.NOT_FOUND));
        verify(repository, never()).findByIdAndJobIdIn(any(), any());
    }

    @Test
    void jobSeekerCannotUseEmployerApplicationAccess() {
        assertThatThrownBy(() -> service.listForEmployer(seeker.getId()))
                .isInstanceOfSatisfying(ApplicationFailure.class,
                        e -> assertThat(e.code()).isEqualTo(ApplicationFailure.Code.FORBIDDEN));
    }

    @Test
    void employerCanShortlistOwnedPendingApplicationAndAuditDecision() {
        User employer = new User("employer@example.com", "hash", UserRole.EMPLOYER);
        Application application = Application.submit(jobId, seeker.getId());
        when(users.findById(employer.getId())).thenReturn(employer);
        when(directory.findAllForEmployer(employer.getId())).thenReturn(
                Map.of(jobId, job(TODAY.plusDays(2), JobSnapshot.State.ACTIVE)));
        when(repository.findByIdAndJobIdIn(application.getId(), Set.of(jobId))).thenReturn(Optional.of(application));

        ApplicationResponse response = service.changeStatus(
                employer.getId(), application.getId(), ApplicationStatus.SHORTLISTED);

        assertThat(response.status()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(response.shortlistedAt()).isNotNull();
        var audit = org.mockito.ArgumentCaptor.forClass(
                com.groupcc2.recruitment.application.entity.ApplicationDecisionAudit.class);
        verify(decisionAudits).saveAndFlush(audit.capture());
        verify(notifications).enqueue(audit.getValue().getId(), application.getId(), "SHORTLISTED");
    }

    @Test
    void employerCannotChangeAFinalApplicationOrAssignPendingStatus() {
        User employer = new User("employer@example.com", "hash", UserRole.EMPLOYER);
        Application application = Application.submit(jobId, seeker.getId());
        application.changeStatus(ApplicationStatus.REJECTED, Instant.now());
        when(users.findById(employer.getId())).thenReturn(employer);
        when(directory.findAllForEmployer(employer.getId())).thenReturn(
                Map.of(jobId, job(TODAY.plusDays(2), JobSnapshot.State.ACTIVE)));
        when(repository.findByIdAndJobIdIn(application.getId(), Set.of(jobId))).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.changeStatus(
                employer.getId(), application.getId(), ApplicationStatus.SHORTLISTED))
                .isInstanceOfSatisfying(ApplicationFailure.class,
                        e -> assertThat(e.code()).isEqualTo(ApplicationFailure.Code.INVALID_STATUS_TRANSITION));
        assertThatThrownBy(() -> service.changeStatus(
                employer.getId(), application.getId(), ApplicationStatus.PENDING))
                .isInstanceOfSatisfying(ApplicationFailure.class,
                        e -> assertThat(e.code()).isEqualTo(ApplicationFailure.Code.INVALID_STATUS_TRANSITION));
        verifyNoInteractions(decisionAudits, notifications);
    }

    @Test
    void employerCanRejectOwnedPendingApplicationAndCreateOneNotificationEvent() {
        User employer = new User("employer@example.com", "hash", UserRole.EMPLOYER);
        Application application = Application.submit(jobId, seeker.getId());
        when(users.findById(employer.getId())).thenReturn(employer);
        when(directory.findAllForEmployer(employer.getId())).thenReturn(
                Map.of(jobId, job(TODAY.plusDays(2), JobSnapshot.State.ACTIVE)));
        when(repository.findByIdAndJobIdIn(application.getId(), Set.of(jobId))).thenReturn(Optional.of(application));

        service.changeStatus(employer.getId(), application.getId(), ApplicationStatus.REJECTED);

        var audit = org.mockito.ArgumentCaptor.forClass(
                com.groupcc2.recruitment.application.entity.ApplicationDecisionAudit.class);
        verify(decisionAudits).saveAndFlush(audit.capture());
        verify(notifications).enqueue(audit.getValue().getId(), application.getId(), "REJECTED");
    }
}
