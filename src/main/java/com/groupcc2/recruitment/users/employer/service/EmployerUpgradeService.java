package com.groupcc2.recruitment.users.employer.service;

import com.groupcc2.recruitment.security.RlsTransactionContext;
import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeRequestBody;
import com.groupcc2.recruitment.users.employer.entity.EmployerRequestStatus;
import com.groupcc2.recruitment.users.employer.entity.EmployerUpgradeRequest;
import com.groupcc2.recruitment.users.employer.repository.EmployerUpgradeRequestRepository;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmployerUpgradeService {
    private final EmployerUpgradeRequestRepository requests;
    private final UserService users;
    private final RlsTransactionContext rls;

    public EmployerUpgradeService(EmployerUpgradeRequestRepository requests, UserService users,
                                  RlsTransactionContext rls) {
        this.requests = requests;
        this.users = users;
        this.rls = rls;
    }

    @Transactional
    public EmployerUpgradeRequest submit(UUID userId, EmployerUpgradeRequestBody body) {
        User user = requireUser(userId, UserRole.JOB_SEEKER);
        if (requests.existsByUserIdAndStatus(userId, EmployerRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An employer request is already pending");
        }
        return requests.save(new EmployerUpgradeRequest(userId, clean(body.companyName()),
                clean(body.businessDescription()), clean(body.contactEmail()), cleanOptional(body.contactPhone()),
                clean(body.location())));
    }

    @Transactional(readOnly = true)
    public List<EmployerUpgradeRequest> ownRequests(UUID userId) {
        requireActiveUser(userId);
        return requests.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<EmployerUpgradeRequest> pending(UUID administratorId) {
        requireAdministrator(administratorId);
        return requests.findAllByStatusOrderByCreatedAtAsc(EmployerRequestStatus.PENDING);
    }

    @Transactional
    public EmployerUpgradeRequest approve(UUID administratorId, UUID requestId) {
        requireAdministrator(administratorId);
        EmployerUpgradeRequest request = findRequest(requestId);
        request.approve(administratorId);
        users.promoteToEmployer(administratorId, request.getUserId());
        return requests.save(request);
    }

    @Transactional
    public EmployerUpgradeRequest reject(UUID administratorId, UUID requestId, String reason) {
        requireAdministrator(administratorId);
        EmployerUpgradeRequest request = findRequest(requestId);
        request.reject(administratorId, clean(reason));
        return requests.save(request);
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
