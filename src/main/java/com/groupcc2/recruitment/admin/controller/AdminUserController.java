package com.groupcc2.recruitment.admin.controller;

import com.groupcc2.recruitment.admin.dto.AssignAdminRoleRequest;
import com.groupcc2.recruitment.users.dto.UserResponse;
import com.groupcc2.recruitment.users.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "User registration, identity, and account management")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "A valid bearer token is required"),
    @ApiResponse(responseCode = "403", description = "The caller must have the ADMIN role")
})
public class AdminUserController {
    private final UserService users;

    public AdminUserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "List users")
    public List<UserResponse> list(@AuthenticationPrincipal Jwt principal) {
        UUID administratorId = userId(principal);
        return users.findAllForAdministrator(administratorId).stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by ID")
    public UserResponse get(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        return UserResponse.from(users.findForAdministrator(userId(principal), id));
    }

    @PatchMapping("/{id}/suspend")
    @Operation(summary = "Suspend a user account")
    public UserResponse suspend(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        users.suspend(userId(principal), id);
        return UserResponse.from(users.findForAdministrator(userId(principal), id));
    }

    @PatchMapping("/{id}/remove")
    @Operation(summary = "Remove a user account")
    public UserResponse remove(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        users.remove(userId(principal), id);
        return UserResponse.from(users.findForAdministrator(userId(principal), id));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Assign administrator role")
    public UserResponse assignRole(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable UUID id,
            @Valid @RequestBody AssignAdminRoleRequest request) {
        return UserResponse.from(users.promoteToAdministrator(userId(principal), id));
    }

    private static UUID userId(Jwt principal) {
        return UUID.fromString(principal.getSubject());
    }
}
