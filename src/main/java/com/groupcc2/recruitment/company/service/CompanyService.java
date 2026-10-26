package com.groupcc2.recruitment.company.service;

import com.groupcc2.recruitment.company.dto.CompanyRequest;
import com.groupcc2.recruitment.company.entity.Company;
import com.groupcc2.recruitment.company.repository.CompanyRepository;
import com.groupcc2.recruitment.security.RlsTransactionContext;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class CompanyService {
    private final CompanyRepository companies;
    private final UserService users;
    private final RlsTransactionContext rls;

    public CompanyService(CompanyRepository companies, UserService users, RlsTransactionContext rls) {
        this.companies = companies;
        this.users = users;
        this.rls = rls;
    }

    @Transactional
    public Company create(UUID employerId, CompanyRequest request) {
        requireEmployer(employerId);
        String name = clean(request.name());
        if (companies.existsByEmployerIdAndName(employerId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Company name already exists");
        }
        try {
            return companies.saveAndFlush(new Company(employerId, name, cleanOptional(request.description()),
                    cleanOptional(request.contactEmail()), cleanOptional(request.contactPhone()),
                    cleanOptional(request.location())));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Company name already exists", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<Company> list(UUID employerId) {
        requireEmployer(employerId);
        return companies.findAllByEmployerIdOrderByName(employerId);
    }

    @Transactional(readOnly = true)
    public Company get(UUID employerId, UUID companyId) {
        requireEmployer(employerId);
        return companies.findByIdAndEmployerId(companyId, employerId).orElseThrow(CompanyService::notFound);
    }

    @Transactional
    public Company update(UUID employerId, UUID companyId, CompanyRequest request) {
        requireEmployer(employerId);
        Company company = companies.findByIdAndEmployerId(companyId, employerId).orElseThrow(CompanyService::notFound);
        String name = clean(request.name());
        if (!name.equals(company.getName()) && companies.existsByEmployerIdAndName(employerId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Company name already exists");
        }
        company.update(name, cleanOptional(request.description()), cleanOptional(request.contactEmail()),
                cleanOptional(request.contactPhone()), cleanOptional(request.location()));
        return companies.saveAndFlush(company);
    }

    private void requireEmployer(UUID employerId) {
        if (employerId == null) throw forbidden();
        rls.setAuthenticatedUser(employerId, UserRole.EMPLOYER);
        var user = users.findById(employerId);
        users.requireActive(user);
        if (user.getRole() != UserRole.EMPLOYER) throw forbidden();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String cleanOptional(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found");
    }
    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Only employers may manage companies");
    }
}
