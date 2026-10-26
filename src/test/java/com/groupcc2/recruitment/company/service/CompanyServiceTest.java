package com.groupcc2.recruitment.company.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.groupcc2.recruitment.company.dto.CompanyRequest;
import com.groupcc2.recruitment.company.entity.Company;
import com.groupcc2.recruitment.company.repository.CompanyRepository;
import com.groupcc2.recruitment.security.RlsTransactionContext;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {
    private static final UUID EMPLOYER_ID = UUID.randomUUID();

    @Mock CompanyRepository companies;
    @Mock UserService users;
    @Mock RlsTransactionContext rls;

    private CompanyService service;

    @BeforeEach
    void setUp() {
        service = new CompanyService(companies, users, rls);
        when(users.findById(EMPLOYER_ID)).thenReturn(new User("employer@example.com", "hash", UserRole.EMPLOYER));
    }

    @Test
    void create_requiresEmployerOwnedCompanyData() {
        when(companies.existsByEmployerIdAndName(EMPLOYER_ID, "Acme")).thenReturn(false);
        when(companies.saveAndFlush(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(EMPLOYER_ID, new CompanyRequest(" Acme ", "Description", null, null, "Blantyre"));

        verify(companies).saveAndFlush(any(Company.class));
        verify(rls).setAuthenticatedUser(EMPLOYER_ID, UserRole.EMPLOYER);
    }

    @Test
    void update_rejectsCompanyNotOwnedByEmployer() {
        UUID companyId = UUID.randomUUID();
        when(companies.findByIdAndEmployerId(companyId, EMPLOYER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(EMPLOYER_ID, companyId,
                new CompanyRequest("Acme", null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(companies, never()).saveAndFlush(any(Company.class));
    }
}
