package com.groupcc2.recruitment.auth.dto;

import java.nio.charset.StandardCharsets;

import com.groupcc2.recruitment.users.entity.UserRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254)
        @Schema(description = "Account email address", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "applicant@example.com")
        String email,
        @NotBlank @Size(min = 12, max = 72)
        @Schema(description = "Account password, 12–72 characters", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "StrongPassword123!")
        String password,
        @NotNull
        @Schema(description = "Self-registration role; ADMIN is not allowed", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"JOB_SEEKER", "EMPLOYER"}, example = "JOB_SEEKER")
        UserRole role) {

    @AssertTrue(message = "ADMIN role cannot be selected during registration")
    public boolean isRoleAllowed() {
        return role == null || role != UserRole.ADMIN;
    }

    @AssertTrue(message = "Password must be at most 72 UTF-8 bytes")
    public boolean isPasswordByteLengthValid() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
