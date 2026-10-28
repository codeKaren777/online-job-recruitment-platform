package com.groupcc2.recruitment.users.employer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.groupcc2.recruitment.security.RlsTransactionContext;
import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeRequestBody;
import com.groupcc2.recruitment.users.employer.entity.EmployerRequestStatus;
import com.groupcc2.recruitment.users.employer.entity.EmployerUpgradeRequest;
import com.groupcc2.recruitment.users.employer.repository.EmployerUpgradeRequestRepository;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
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
    private EmployerUpgradeService service;

    @BeforeEach
    void setUp() {
        service = new EmployerUpgradeService(requests, users, rls);
    }

    @Test
    void seekerCanSubmitOnlyOnePendingRequest() {
        User seeker = new User("seeker@example.com", "hash", UserRole.JOB_SEEKER);
        when(users.findById(seekerId)).thenReturn(seeker);
        when(requests.existsByUserIdAndStatus(seekerId, EmployerRequestStatus.PENDING)).thenReturn(false);
        when(requests.save(any(EmployerUpgradeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployerUpgradeRequest result = service.submit(seekerId,
                new EmployerUpgradeRequestBody("Acme", "Recruitment", "contact@acme.example", null, "Blantyre"));

        assertThat(result.getStatus()).isEqualTo(EmployerRequestStatus.PENDING);
        verify(requests).save(any(EmployerUpgradeRequest.class));

        when(requests.existsByUserIdAndStatus(seekerId, EmployerRequestStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> service.submit(seekerId,
                new EmployerUpgradeRequestBody("Other", "Recruitment", "other@acme.example", null, "Blantyre")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void administratorApprovalPromotesApplicantAndClosesRequest() {
        User admin = new User("admin@example.com", "hash", UserRole.ADMIN);
        EmployerUpgradeRequest request = new EmployerUpgradeRequest(seekerId, "Acme", "Recruitment",
                "contact@acme.example", null, "Blantyre");
        when(users.findById(adminId)).thenReturn(admin);
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(requests.save(any(EmployerUpgradeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployerUpgradeRequest result = service.approve(adminId, requestId);

        assertThat(result.getStatus()).isEqualTo(EmployerRequestStatus.APPROVED);
        verify(users).promoteToEmployer(adminId, seekerId);
        InOrder authorization = inOrder(rls, users, requests);
        authorization.verify(rls).setSelfOnlyUser(adminId);
        authorization.verify(users).findById(adminId);
        authorization.verify(users).requireActive(admin);
        authorization.verify(rls).setAuthenticatedUser(adminId, UserRole.ADMIN);
        authorization.verify(requests).findById(requestId);
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
