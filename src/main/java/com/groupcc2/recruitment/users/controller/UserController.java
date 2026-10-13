package com.groupcc2.recruitment.users.controller;

import com.groupcc2.recruitment.users.dto.RegisterUserRequest;
import com.groupcc2.recruitment.users.dto.UserResponse;
import com.groupcc2.recruitment.users.service.AdminRegistrationNotAllowedException;
import com.groupcc2.recruitment.users.service.DuplicateEmailException;
import com.groupcc2.recruitment.users.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping("/register")
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
