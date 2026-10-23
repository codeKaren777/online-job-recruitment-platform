package com.groupcc2.recruitment.auth.dto;

import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @NotBlank @Email @Size(max = 254)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "applicant@example.com")
        String email,
        @NotBlank @Size(max = 72)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "StrongPassword123!")
        String password) {

    @AssertTrue(message = "Password must be at most 72 UTF-8 bytes")
    public boolean isPasswordByteLengthValid() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
