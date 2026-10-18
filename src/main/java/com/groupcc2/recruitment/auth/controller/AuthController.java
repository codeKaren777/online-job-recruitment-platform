package com.groupcc2.recruitment.auth.controller;

import com.groupcc2.recruitment.auth.dto.CurrentUserResponse;
import com.groupcc2.recruitment.auth.dto.ChangePasswordRequest;
import com.groupcc2.recruitment.auth.dto.ForgotPasswordRequest;
import com.groupcc2.recruitment.auth.dto.LoginRequest;
import com.groupcc2.recruitment.auth.dto.LoginResponse;
import com.groupcc2.recruitment.auth.dto.PasswordResetRequest;
import com.groupcc2.recruitment.auth.dto.PasswordResetResponse;
import com.groupcc2.recruitment.auth.dto.RegisterRequest;
import com.groupcc2.recruitment.auth.dto.RegisterResponse;
import com.groupcc2.recruitment.auth.service.AuthService;
import com.groupcc2.recruitment.auth.service.InvalidPasswordResetTokenException;
import jakarta.validation.Valid;
import com.groupcc2.recruitment.auth.service.EmailAlreadyRegisteredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt principal) {
        authService.logout(
                principal.getSubject(),
                principal.getClaimAsString("role"),
                tokenVersion(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(
                principal.getSubject(),
                principal.getClaimAsString("role"),
                tokenVersion(principal),
                request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<PasswordResetResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.accepted().body(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> currentUser(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok(authService.currentUser(
                principal.getSubject(), principal.getClaimAsString("role")));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<Void> duplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Void> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    public ResponseEntity<Void> invalidResetToken() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Void> invalidRequestBody() {
        return ResponseEntity.badRequest().build();
    }

    private int tokenVersion(Jwt principal) {
        Object claim = principal.getClaims().get("tokenVersion");
        if (claim instanceof Number version && version.longValue() >= 0
                && version.longValue() <= Integer.MAX_VALUE) {
            return version.intValue();
        }
        throw new BadCredentialsException("Invalid authenticated identity");
    }
}
