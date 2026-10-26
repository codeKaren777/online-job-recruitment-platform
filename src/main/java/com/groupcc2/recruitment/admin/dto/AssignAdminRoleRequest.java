package com.groupcc2.recruitment.admin.dto;

import com.groupcc2.recruitment.users.entity.UserRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AssignAdminRoleRequest(@NotNull UserRole role) {
    @AssertTrue(message = "Only the ADMIN role can be assigned through this endpoint")
    public boolean isAdminRole() {
        return role == UserRole.ADMIN;
    }
}
