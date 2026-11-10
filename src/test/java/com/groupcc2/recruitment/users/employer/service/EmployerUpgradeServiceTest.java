package com.groupcc2.recruitment.users.employer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.groupcc2.recruitment.security.RlsTransactionContext;
import com.groupcc2.recruitment.users.employer.entity.EmployerRequestStatus;
import com.groupcc2.recruitment.users.employer.entity.EmployerUpgradeRequest;
import com.groupcc2.recruitment.users.employer.repository.EmployerUpgradeRequestRepository;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
import com.groupcc2.recruitment.company.repository.CompanyRepository;
import com.groupcc2.recruitment.company.entity.Company;
import com.groupcc2.recruitment.common.notification.NotificationEmailSender;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EmployerUpgradeServiceTest {
    private final UUID seekerId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();

    @Mock EmployerUpgradeRequestRepository requests;
    @Mock UserService users;
    @Mock RlsTransactionContext rls;
    @Mock CompanyRepository companies;
    @Mock ObjectProvider<NotificationEmailSender> notificationEmails;
    @Mock NotificationEmailSender notificationEmailSender;
    private EmployerUpgradeService service;

    @BeforeEach
    void setUp() {
        service = new EmployerUpgradeService(requests, users, rls, companies);
    }

    @Test
    void administratorApprovalPromotesApplicantAndClosesRequest() {
        User admin = new User("admin@example.com", "hash", UserRole.ADMIN);
        EmployerUpgradeRequest request = new EmployerUpgradeRequest(seekerId, "Acme", "Recruitment",
                "contact@acme.example", "+265999000111", "Blantyre", "VAT-001", "REG-001",
                "documents/company-verifications/test", "registration.pdf", "application/pdf", 42);
        when(users.findById(adminId)).thenReturn(admin);
        when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        UUID companyId = UUID.randomUUID();
        when(companies.saveAndFlush(any(Company.class))).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            var id = Company.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(company, companyId);
            return company;
        });
        when(requests.save(any(EmployerUpgradeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployerUpgradeRequest result = service.approve(adminId, requestId);

        assertThat(result.getStatus()).isEqualTo(EmployerRequestStatus.APPROVED);
        assertThat(result.getCompanyId()).isEqualTo(companyId);
        assertThat(result.getReviewedBy()).isEqualTo(adminId);
        assertThat(result.getReviewedAt()).isNotNull();
        verify(users).promoteToEmployer(adminId, seekerId);
        verify(companies).saveAndFlush(any(Company.class));
        InOrder authorization = inOrder(rls, users, requests);
        authorization.verify(rls).setSelfOnlyUser(adminId);
        authorization.verify(users).findById(adminId);
        authorization.verify(users).requireActive(admin);
        authorization.verify(rls).setAuthenticatedUser(adminId, UserRole.ADMIN);
        authorization.verify(requests).findByIdForUpdate(requestId);
    }

    @Test
    void repeatedApprovalDoesNotRepeatCompanyCreationOrRoleUpgrade() throws Exception {
        User admin = new User("admin@example.com", "hash", UserRole.ADMIN);
        EmployerUpgradeRequest request = new EmployerUpgradeRequest(seekerId, "Acme", "Recruitment",
                "contact@acme.example", "+265999000111", "Blantyre", "VAT-001", "REG-001",
                "documents/company-verifications/test", "registration.pdf", "application/pdf", 42);
        when(users.findById(adminId)).thenReturn(admin);
        when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(companies.saveAndFlush(any(Company.class))).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            var id = Company.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(company, UUID.randomUUID());
            return company;
        });
        when(requests.save(any(EmployerUpgradeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployerUpgradeRequest approved = service.approve(adminId, requestId);
        EmployerUpgradeRequest retry = service.approve(adminId, requestId);

        assertThat(retry).isSameAs(approved);
        verify(companies, times(1)).saveAndFlush(any(Company.class));
        verify(users, times(1)).promoteToEmployer(adminId, seekerId);
    }

    @Test
    void rejectionStoresReasonWithoutCompanyOrEmployerUpgradeAndCannotBeApproved() {
        User admin = new User("admin@example.com", "hash", UserRole.ADMIN);
        EmployerUpgradeRequest request = new EmployerUpgradeRequest(seekerId, "Acme", "Recruitment",
                "contact@acme.example", "+265999000111", "Blantyre", "VAT-001", "REG-001",
                "documents/company-verifications/test", "registration.pdf", "application/pdf", 42);
        when(users.findById(adminId)).thenReturn(admin);
        when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(requests.save(any(EmployerUpgradeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployerUpgradeRequest rejected = service.reject(adminId, requestId, "Registration could not be verified.");

        assertThat(rejected.getStatus()).isEqualTo(EmployerRequestStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Registration could not be verified.");
        verifyNoInteractions(companies);
        verify(users, never()).promoteToEmployer(any(), any());
        assertThatThrownBy(() -> service.approve(adminId, requestId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verifyNoInteractions(companies);
    }

    @Test
    void rejectionNotificationContainsTheApplicantsAddressAndReason() {
        User admin = new User("admin@example.com", "hash", UserRole.ADMIN);
        User applicant = new User("seeker@example.com", "hash", UserRole.JOB_SEEKER);
        EmployerUpgradeService notifyingService = new EmployerUpgradeService(
                requests, users, rls, notificationEmails, companies);
        EmployerUpgradeRequest request = new EmployerUpgradeRequest(seekerId, "Acme", "Recruitment",
                "contact@acme.example", "+265999000111", "Blantyre", "VAT-001", "REG-001",
                "documents/company-verifications/test", "registration.pdf", "application/pdf", 42);
        when(users.findById(adminId)).thenReturn(admin);
        when(users.findById(seekerId)).thenReturn(applicant);
        when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(requests.save(any(EmployerUpgradeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationEmails.getIfAvailable()).thenReturn(notificationEmailSender);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();

        notifyingService.reject(adminId, requestId, "Registration could not be verified.");

        verifyNoInteractions(notificationEmailSender);
        org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        verify(notificationEmailSender).sendEmployerDecision("seeker@example.com", "Acme", "REJECTED",
                "Registration could not be verified.");
        org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void applicantRetrievalIsScopedToTheAuthenticatedOwner() {
        User applicant = new User("seeker@example.com", "hash", UserRole.JOB_SEEKER);
        when(users.findById(seekerId)).thenReturn(applicant);
        when(requests.findByIdAndUserId(requestId, seekerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ownRequest(seekerId, requestId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(requests).findByIdAndUserId(requestId, seekerId);
        verify(requests, never()).findById(requestId);
    }

    @Test
    void rejectionReasonCannotBeBlank() {
        User admin = new User("admin@example.com", "hash", UserRole.ADMIN);
        when(users.findById(adminId)).thenReturn(admin);

        assertThatThrownBy(() -> service.reject(adminId, requestId, "  "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(requests, never()).findByIdForUpdate(any());
    }

    @Test
    void nonAdministratorCannotReviewRequests() {
        User seeker = new User("seeker@example.com", "hash", UserRole.JOB_SEEKER);
        when(users.findById(seekerId)).thenReturn(seeker);

        assertThatThrownBy(() -> service.pending(seekerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(rls).setSelfOnlyUser(seekerId);
        verify(rls, never()).setAuthenticatedUser(any(), any());
        verifyNoInteractions(requests);
    }

    @Test
    void employerCannotUseAdminReviewService() {
        User employer = new User("employer@example.com", "hash", UserRole.EMPLOYER);
        when(users.findById(seekerId)).thenReturn(employer);

        assertThatThrownBy(() -> service.pending(seekerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(rls).setSelfOnlyUser(seekerId);
        verify(rls, never()).setAuthenticatedUser(any(), any());
        verifyNoInteractions(requests);
    }
}
