package com.groupcc2.recruitment.auth.dto;

import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 12, max = 72) String newPassword) {

    @AssertTrue(message = "New password must be at most 72 UTF-8 bytes")
    public boolean isNewPasswordByteLengthValid() {
        return newPassword == null
                || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @AssertTrue(message = "New password must differ from the current password")
    public boolean isNewPasswordDifferent() {
        return currentPassword == null || newPassword == null || !currentPassword.equals(newPassword);
    }
}
