package com.groupcc2.recruitment.users.dto;

import java.nio.charset.StandardCharsets;

import com.groupcc2.recruitment.users.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Email @Size(max = 254)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "account@example.com")
        String email,
        @NotBlank @Size(min = 12, max = 72)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "StrongPassword123!")
        String password,
        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"JOB_SEEKER", "EMPLOYER"})
        UserRole role) {

    @AssertTrue(message = "Only JOB_SEEKER or EMPLOYER role can be created")
    public boolean isRoleAllowed() {
        return role == UserRole.JOB_SEEKER || role == UserRole.EMPLOYER;
    }

    @AssertTrue(message = "Password must be at most 72 UTF-8 bytes")
    public boolean isPasswordByteLengthValid() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
