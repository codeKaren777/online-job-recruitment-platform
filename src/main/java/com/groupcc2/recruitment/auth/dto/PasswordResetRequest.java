package com.groupcc2.recruitment.auth.dto;

import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Size(max = 128) String resetToken,
        @NotBlank @Size(min = 12, max = 72) String newPassword) {

    @AssertTrue(message = "New password must be at most 72 UTF-8 bytes")
    public boolean isNewPasswordByteLengthValid() {
        return newPassword == null
                || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
