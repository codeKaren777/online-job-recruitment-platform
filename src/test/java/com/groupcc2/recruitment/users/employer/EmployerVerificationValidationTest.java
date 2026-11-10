package com.groupcc2.recruitment.users.employer;

import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeRequestBody;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmployerVerificationValidationTest {
    private static Validator validator;
    private static jakarta.validation.ValidatorFactory factory;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() { factory.close(); }

    @Test
    void rejectsMissingRequiredFields() {
        EmployerUpgradeRequestBody body = new EmployerUpgradeRequestBody(" ", " ", "bad", " ", " ", " ", " ");
        assertThat(validator.validate(body)).hasSizeGreaterThanOrEqualTo(7);
    }

    @Test
    void rejectsInvalidContactEmailAndPhone() {
        EmployerUpgradeRequestBody body = new EmployerUpgradeRequestBody("Acme", "Recruitment",
                "not-an-email", "phone?", "Blantyre", "VAT-001", "REG-001");
        assertThat(validator.validate(body)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("contactEmail", "contactPhone");
    }

    @Test
    void acceptsCompleteVerificationDetails() {
        EmployerUpgradeRequestBody body = new EmployerUpgradeRequestBody("Acme", "Recruitment",
                "contact@acme.example", "+265999000111", "Blantyre", "VAT-001", "REG-001");
        assertThat(validator.validate(body)).isEmpty();
    }

    @Test
    void rejectsLegacyPlaceholderIdentifiers() {
        EmployerUpgradeRequestBody body = new EmployerUpgradeRequestBody("Acme", "Recruitment",
                "contact@acme.example", "+265999000111", "Blantyre", "legacy", "LEGACY");
        assertThat(validator.validate(body)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("vatNumber", "registrationNumber");
    }
}
