package com.groupcc2.recruitment.users.dto;

import com.groupcc2.recruitment.users.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public user registration request")
public record RegisterUserRequest(
        @Schema(description = "Unique email address", example = "person@example.com")
        @NotBlank @Email @Size(max = 320) String email,
        @Schema(description = "Password (12 to 72 characters)", example = "correct horse battery")
        @NotBlank @Size(min = 12, max = 72) String password,
        @Schema(description = "Publicly supported account role. ADMIN cannot be registered.",
                allowableValues = {"JOB_SEEKER", "EMPLOYER"}, example = "JOB_SEEKER")
        @NotNull UserRole role) {
}
