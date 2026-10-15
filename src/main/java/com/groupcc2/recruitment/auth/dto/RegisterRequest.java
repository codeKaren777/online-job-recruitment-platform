package com.groupcc2.recruitment.auth.dto;

import java.nio.charset.StandardCharsets;

import com.groupcc2.recruitment.auth.entity.UserRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotNull UserRole role) {

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
