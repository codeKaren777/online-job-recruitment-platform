package com.groupcc2.recruitment.users.controller;

import com.groupcc2.recruitment.users.dto.RegisterUserRequest;
import com.groupcc2.recruitment.users.dto.UserResponse;
import com.groupcc2.recruitment.users.service.AdminRegistrationNotAllowedException;
import com.groupcc2.recruitment.users.service.DuplicateEmailException;
import com.groupcc2.recruitment.users.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User registration and identity")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a user", description = "Public registration accepts JOB_SEEKER or EMPLOYER. "
            + "ADMIN accounts cannot be created through this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or unsupported role", content = @Content),
            @ApiResponse(responseCode = "403", description = "Administrator registration is forbidden", content = @Content),
            @ApiResponse(responseCode = "409", description = "Email address is already registered", content = @Content)
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponse.from(users.register(request.email(), request.password(), request.role())));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<Void> duplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(AdminRegistrationNotAllowedException.class)
    ResponseEntity<Void> administratorRegistration() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
