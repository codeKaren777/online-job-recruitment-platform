package com.groupcc2.recruitment.users.employer.service;

import com.groupcc2.recruitment.company.repository.CompanyRepository;
import com.groupcc2.recruitment.profile.storage.DocumentValidator;
import com.groupcc2.recruitment.profile.storage.PrivateDocumentStorage;
import com.groupcc2.recruitment.security.RlsTransactionContext;
import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeRequestBody;
import com.groupcc2.recruitment.users.employer.entity.EmployerUpgradeRequest;
import com.groupcc2.recruitment.users.employer.repository.EmployerUpgradeRequestRepository;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmployerVerificationSubmissionTest {
    private final EmployerUpgradeRequestRepository requests = mock(EmployerUpgradeRequestRepository.class);
    private final UserService users = mock(UserService.class);
    private final RlsTransactionContext rls = mock(RlsTransactionContext.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final ObjectProvider<PrivateDocumentStorage> storageProvider = mock(ObjectProvider.class);
    private final PrivateDocumentStorage storage = mock(PrivateDocumentStorage.class);
    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void savesPrivatePdfAndApplicationMetadataForAuthenticatedApplicant() {
        EmployerUpgradeService service = new EmployerUpgradeService(requests, users, rls, null, companies,
                new DocumentValidator(), storageProvider);
        when(users.findById(userId)).thenReturn(new User("seeker@example.com", "hash", UserRole.JOB_SEEKER));
        when(requests.existsByUserIdAndStatus(any(), any())).thenReturn(false);
        when(requests.saveAndFlush(any(EmployerUpgradeRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(storageProvider.getObject()).thenReturn(storage);
        TransactionSynchronizationManager.initSynchronization();

        EmployerUpgradeRequest saved = service.submit(userId,
                new EmployerUpgradeRequestBody("Acme", "Recruitment", "contact@acme.example",
                        "+265999000111", "Blantyre", " vat-001 ", " reg-001 "),
                new MockMultipartFile("document", "registration.pdf", "application/pdf",
                        "%PDF-1.7\n1 0 obj <<>> endobj\n%%EOF\n".getBytes()));

        assertThat(saved.getVatNumber()).isEqualTo("VAT-001");
        assertThat(saved.getRegistrationNumber()).isEqualTo("REG-001");
        assertThat(saved.getDocumentOriginalFilename()).isEqualTo("registration.pdf");
        assertThat(saved.getDocumentContentType()).isEqualTo("application/pdf");
        assertThat(saved.getDocumentSize()).isPositive();
        assertThat(saved.getDocumentStorageKey()).startsWith("documents/company-verifications/");
        assertThat(saved.getCompanyId()).isNull();
        verify(storage).upload(startsWith("documents/company-verifications/"), any(), eq("application/pdf"));
        verify(requests).saveAndFlush(any(EmployerUpgradeRequest.class));
        verifyNoInteractions(companies);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(
                        org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED));
    }

    @Test
    void deletesUploadedDocumentWhenDatabaseTransactionRollsBack() {
        EmployerUpgradeService service = new EmployerUpgradeService(requests, users, rls, null, companies,
                new DocumentValidator(), storageProvider);
        when(users.findById(userId)).thenReturn(new User("seeker@example.com", "hash", UserRole.JOB_SEEKER));
        when(requests.existsByUserIdAndStatus(any(), any())).thenReturn(false);
        when(requests.saveAndFlush(any(EmployerUpgradeRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        when(storageProvider.getObject()).thenReturn(storage);
        TransactionSynchronizationManager.initSynchronization();

        EmployerUpgradeRequest saved = service.submit(userId,
                new EmployerUpgradeRequestBody("Acme", "Recruitment", "contact@acme.example",
                        "+265999000111", "Blantyre", "VAT-002", "REG-002"),
                new MockMultipartFile("document", "registration.pdf", "application/pdf",
                        "%PDF-1.7\n1 0 obj <<>> endobj\n%%EOF\n".getBytes()));

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(
                        org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).delete(saved.getDocumentStorageKey());
    }
}
