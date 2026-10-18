package com.groupcc2.recruitment.auth.dto;

import java.util.UUID;

import com.groupcc2.recruitment.users.entity.AccountStatus;
import com.groupcc2.recruitment.users.entity.UserRole;

public record CurrentUserResponse(UUID id, String email, UserRole role, AccountStatus status) {
}
