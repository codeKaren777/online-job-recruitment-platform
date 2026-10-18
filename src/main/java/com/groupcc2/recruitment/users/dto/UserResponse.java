package com.groupcc2.recruitment.users.dto;

import java.time.Instant;
import java.util.UUID;

import com.groupcc2.recruitment.users.entity.AccountStatus;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Safe user identity response. Credential data is never returned.")
public record UserResponse(UUID id, String email, UserRole role, AccountStatus accountStatus,
                           Instant createdAt, Instant updatedAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getAccountStatus(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
