package com.groupcc2.recruitment.auth;

import java.util.Set;

import com.groupcc2.recruitment.auth.dto.LoginRequest;
import com.groupcc2.recruitment.auth.dto.ChangePasswordRequest;
import com.groupcc2.recruitment.auth.dto.ForgotPasswordRequest;
import com.groupcc2.recruitment.auth.dto.PasswordResetRequest;
import com.groupcc2.recruitment.auth.dto.RegisterRequest;
import com.groupcc2.recruitment.users.entity.UserRole;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDtoValidationTest {
    private static Validator validator;
    private static AutoCloseable validatorFactory;

    @BeforeAll
    static void setUpValidator() {
        var factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        validatorFactory = factory;
    }

    @AfterAll
    static void closeValidatorFactory() throws Exception {
        validatorFactory.close();
    }

    @Test
    void acceptsJobSeekerOrUnspecifiedRegistrationRole() {
        assertThat(validator.validate(new RegisterRequest("person@example.com",
                "StrongPassword123!", UserRole.JOB_SEEKER))).isEmpty();
        assertThat(validator.validate(new RegisterRequest("person@example.com",
                "StrongPassword123!"))).isEmpty();
    }

    @Test
    void rejectsElevatedRegistrationRolesAndInvalidFields() {
        Set<ConstraintViolation<RegisterRequest>> adminViolations = validator.validate(
                new RegisterRequest("person@example.com", "StrongPassword123!", UserRole.ADMIN));
        assertThat(adminViolations).extracting(ConstraintViolation::getMessage)
                .contains("Only JOB_SEEKER role can be selected during registration");
        assertThat(validator.validate(new RegisterRequest(
                "person@example.com", "StrongPassword123!", UserRole.EMPLOYER))).isNotEmpty();

        Set<ConstraintViolation<RegisterRequest>> fieldViolations = validator.validate(
                new RegisterRequest("not-an-email", "short"));
        assertThat(fieldViolations).isNotEmpty();
        assertThat(validator.validate(new RegisterRequest(
                "person@example.com", "é".repeat(37)))).isNotEmpty();
    }

    @Test
    void validatesLoginEmailAndPassword() {
        assertThat(validator.validate(new LoginRequest("person@example.com", "password"))).isEmpty();
        assertThat(validator.validate(new LoginRequest("not-an-email", ""))).isNotEmpty();
        assertThat(validator.validate(new LoginRequest("person@example.com", "é".repeat(37)))).isNotEmpty();
    }

    @Test
    void validatesPasswordRecoveryAndChangeRequestsIncludingBcryptByteLimit() {
        assertThat(validator.validate(new ForgotPasswordRequest("person@example.com"))).isEmpty();
        assertThat(validator.validate(
                new PasswordResetRequest("a".repeat(43), "NewStrongPassword123!"))).isEmpty();
        assertThat(validator.validate(
                new ChangePasswordRequest("OldStrongPassword123!", "NewStrongPassword123!"))).isEmpty();
        assertThat(validator.validate(
                new ChangePasswordRequest("same-password", "same-password"))).isNotEmpty();
        assertThat(validator.validate(
                new PasswordResetRequest("token", "é".repeat(40)))).isNotEmpty();
        assertThat(validator.validate(new ForgotPasswordRequest("invalid-email"))).isNotEmpty();
    }
}
