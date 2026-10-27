package com.groupcc2.recruitment.users.controller;

import com.groupcc2.recruitment.users.dto.UserResponse;
import com.groupcc2.recruitment.users.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User registration, identity, and account management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user")
    public UserResponse currentUser(@AuthenticationPrincipal Jwt principal) {
        return UserResponse.from(users.findById(userId(principal)));
    }

    private static UUID userId(Jwt principal) {
        return UUID.fromString(principal.getSubject());
    }
}
