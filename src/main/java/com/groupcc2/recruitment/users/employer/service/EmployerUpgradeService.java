package com.groupcc2.recruitment.users.employer.service;

import com.groupcc2.recruitment.security.RlsTransactionContext;
import com.groupcc2.recruitment.common.notification.NotificationEmailSender;
import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeRequestBody;
import com.groupcc2.recruitment.users.employer.entity.EmployerRequestStatus;
import com.groupcc2.recruitment.users.employer.entity.EmployerUpgradeRequest;
import com.groupcc2.recruitment.users.employer.repository.EmployerUpgradeRequestRepository;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
import com.groupcc2.recruitment.company.entity.Company;
import com.groupcc2.recruitment.company.repository.CompanyRepository;
import com.groupcc2.recruitment.profile.storage.DocumentValidator;
import com.groupcc2.recruitment.profile.storage.PrivateDocumentStorage;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class EmployerUpgradeService {
    private final EmployerUpgradeRequestRepository requests;
    private final UserService users;
    private final RlsTransactionContext rls;
    private final ObjectProvider<NotificationEmailSender> notificationEmails;
    private final CompanyRepository companies;
    private final DocumentValidator documentValidator;
    private final ObjectProvider<PrivateDocumentStorage> storage;

    public EmployerUpgradeService(EmployerUpgradeRequestRepository requests, UserService users,
                                  RlsTransactionContext rls) {
        this(requests, users, rls, null, null, null, null);
    }

    public EmployerUpgradeService(EmployerUpgradeRequestRepository requests, UserService users,
                                  RlsTransactionContext rls, CompanyRepository companies) {
        this(requests, users, rls, null, companies, null, null);
    }

    public EmployerUpgradeService(EmployerUpgradeRequestRepository requests, UserService users,
                                  RlsTransactionContext rls,
                                  ObjectProvider<NotificationEmailSender> notificationEmails,
                                  CompanyRepository companies) {
        this(requests, users, rls, notificationEmails, companies, null, null);
    }

    @Autowired
    public EmployerUpgradeService(EmployerUpgradeRequestRepository requests, UserService users,
                                  RlsTransactionContext rls,
                                  ObjectProvider<NotificationEmailSender> notificationEmails,
                                  CompanyRepository companies, DocumentValidator documentValidator,
                                  ObjectProvider<PrivateDocumentStorage> storage) {
        this.requests = requests;
        this.users = users;
        this.rls = rls;
        this.notificationEmails = notificationEmails;
        this.companies = companies;
        this.documentValidator = documentValidator;
        this.storage = storage;
    }

    @Transactional
    public EmployerUpgradeRequest submit(UUID userId, EmployerUpgradeRequestBody body, MultipartFile file) {
        requireUser(userId, UserRole.JOB_SEEKER);
        if (requests.existsByUserIdAndStatus(userId, EmployerRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An employer request is already pending");
        }
        if (documentValidator == null || storage == null || storage.getIfAvailable() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Private document storage is unavailable");
        }
        String key = "documents/company-verifications/" + UUID.randomUUID();
        PrivateDocumentStorage privateStorage = storage.getObject();
        try (DocumentValidator.Validated validated = documentValidator.validate(file)) {
            try {
                privateStorage.upload(key, validated.path(), "application/pdf");
            } catch (RuntimeException exception) {
                throw new com.groupcc2.recruitment.profile.service.ProfileFailure(
                        com.groupcc2.recruitment.profile.service.ProfileFailure.Code.STORAGE_UNAVAILABLE);
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { privateStorage.delete(key); } catch (RuntimeException ignored) { }
                    }
                }
            });
            try {
                return requests.saveAndFlush(new EmployerUpgradeRequest(userId, clean(body.companyName()),
                        clean(body.businessDescription()), clean(body.contactEmail()), cleanOptional(body.contactPhone()),
                        clean(body.location()), cleanIdentifier(body.vatNumber()),
                        cleanIdentifier(body.registrationNumber()), key,
                        validated.filename(), "application/pdf", validated.size()));
            } catch (DataIntegrityViolationException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "An employer request is already pending or its identifiers are invalid", exception);
            }
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not process registration document");
        }
    }

    @Transactional(readOnly = true)
    public java.net.URI registrationDocument(UUID administratorId, UUID requestId) {
        requireAdministrator(administratorId);
        EmployerUpgradeRequest request = findRequest(requestId);
        if (request.getDocumentStorageKey().startsWith("legacy/")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration document not available");
        }
        if (storage == null || storage.getIfAvailable() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Private document storage is unavailable");
        }
        return storage.getObject().signGet(request.getDocumentStorageKey(), Duration.ofMinutes(2));
    }

    @Transactional(readOnly = true)
    public List<EmployerUpgradeRequest> ownRequests(UUID userId) {
        requireActiveUser(userId);
        return requests.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public EmployerUpgradeRequest ownRequest(UUID userId, UUID requestId) {
        requireActiveUser(userId);
        return requests.findByIdAndUserId(requestId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Employer request not found"));
    }

    @Transactional(readOnly = true)
    public List<EmployerUpgradeRequest> pending(UUID administratorId) {
        requireAdministrator(administratorId);
        return requests.findAllByStatusOrderByCreatedAtAsc(EmployerRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public EmployerUpgradeRequest getForAdministrator(UUID administratorId, UUID requestId) {
        requireAdministrator(administratorId);
        return findRequest(requestId);
    }

    @Transactional
    public EmployerUpgradeRequest approve(UUID administratorId, UUID requestId) {
        requireAdministrator(administratorId);
        EmployerUpgradeRequest request = requests.findByIdForUpdate(requestId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Employer request not found"));
        if (request.getStatus() == EmployerRequestStatus.APPROVED && request.getCompanyId() != null) return request;
        if (request.getStatus() != EmployerRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employer request has already been reviewed");
        }
        if (request.getDocumentStorageKey().startsWith("legacy/")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This request has no registration document and cannot be approved");
        }
        users.promoteToEmployer(administratorId, request.getUserId());
        if (companies == null) throw new IllegalStateException("Company integration is unavailable");
        Company company;
        try {
            company = companies.saveAndFlush(new Company(request.getUserId(), request.getCompanyName(),
                    request.getBusinessDescription(), request.getContactEmail(), request.getContactPhone(),
                    request.getLocation(), request.getVatNumber(), request.getRegistrationNumber()));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Verified company identifiers or name already exist", exception);
        }
        try {
            request.approve(administratorId);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employer request has already been reviewed");
        }
        request.attachCompany(company.getId());
        EmployerUpgradeRequest saved = requests.save(request);
        notifyDecisionAfterCommit(saved);
        return saved;
    }

    @Transactional
    public EmployerUpgradeRequest reject(UUID administratorId, UUID requestId, String reason) {
        requireAdministrator(administratorId);
        String rejectionReason = clean(reason);
        if (rejectionReason.isBlank() || rejectionReason.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection reason of 1 to 2000 characters is required");
        }
        EmployerUpgradeRequest request = requests.findByIdForUpdate(requestId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Employer request not found"));
        try {
            request.reject(administratorId, rejectionReason);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employer request has already been reviewed");
        }
        EmployerUpgradeRequest saved = requests.save(request);
        notifyDecisionAfterCommit(saved);
        return saved;
    }

    private void notifyDecisionAfterCommit(EmployerUpgradeRequest request) {
        NotificationEmailSender sender = notificationEmails == null
                ? null : notificationEmails.getIfAvailable();
        if (sender != null) {
            String recipient = users.findById(request.getUserId()).getEmail();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    sender.sendEmployerDecision(recipient, request.getCompanyName(),
                            request.getStatus().name(), request.getRejectionReason());
                }
            });
        }
    }

    private static String cleanIdentifier(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private User requireUser(UUID userId, UserRole role) {
        if (userId == null) throw forbidden();
        rls.setAuthenticatedUser(userId, role);
        User user = users.findById(userId);
        users.requireActive(user);
        if (user.getRole() != role) throw forbidden();
        return user;
    }

    private User requireActiveUser(UUID userId) {
        if (userId == null) throw forbidden();
        rls.setAuthenticatedUser(userId, UserRole.JOB_SEEKER);
        User user = users.findById(userId);
        users.requireActive(user);
        if (user.getRole() != UserRole.JOB_SEEKER && user.getRole() != UserRole.EMPLOYER) {
            throw forbidden();
        }
        return user;
    }

    private void requireAdministrator(UUID administratorId) {
        if (administratorId == null) throw forbidden();
        rls.setSelfOnlyUser(administratorId);
        User administrator = users.findById(administratorId);
        users.requireActive(administrator);
        if (administrator.getRole() != UserRole.ADMIN) throw forbidden();
        rls.setAuthenticatedUser(administratorId, UserRole.ADMIN);
    }

    private EmployerUpgradeRequest findRequest(UUID requestId) {
        return requests.findById(requestId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Employer request not found"));
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String cleanOptional(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }
    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted");
    }
}
