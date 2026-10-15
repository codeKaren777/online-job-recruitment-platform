package com.groupcc2.recruitment.auth.dto;

import java.util.UUID;

import com.groupcc2.recruitment.auth.entity.AccountStatus;
import com.groupcc2.recruitment.auth.entity.UserRole;

public record CurrentUserResponse(UUID id, String email, UserRole role, AccountStatus status) {
}
