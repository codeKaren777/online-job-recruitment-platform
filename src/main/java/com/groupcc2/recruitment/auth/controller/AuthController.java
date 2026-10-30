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
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registration, login, password changes, and password recovery")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a job seeker account", description = "Creates a new account. Public registration accepts the JOB_SEEKER role only; credentials must satisfy the request validation rules.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Email, password, and optional JOB_SEEKER role.", required = true, content = @io.swagger.v3.oas.annotations.media.Content(examples = @ExampleObject(value = "{\"email\":\"applicant@example.com\",\"password\":\"StrongPassword123!\",\"role\":\"JOB_SEEKER\"}"))))
    @ApiResponses({@ApiResponse(responseCode="201", description="Account created; returns the registered account details."), @ApiResponse(responseCode="400", description="Request body is malformed or fails validation."), @ApiResponse(responseCode="409", description="Email is already registered."), @ApiResponse(responseCode="500", description="Unexpected server error.")})
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Authenticates an account with its email and password and returns a bearer token.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Account credentials.", required = true, content = @io.swagger.v3.oas.annotations.media.Content(examples = @ExampleObject(value = "{\"email\":\"applicant@example.com\",\"password\":\"StrongPassword123!\"}"))))
    @ApiResponses({@ApiResponse(responseCode="200", description="Credentials accepted; returns the token response."), @ApiResponse(responseCode="400", description="Request body is malformed or fails validation."), @ApiResponse(responseCode="401", description="Credentials are invalid."), @ApiResponse(responseCode="500", description="Unexpected server error.")})
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Log out", description = "Invalidates the authenticated account's current token version. Requires a valid bearer token.")
    @ApiResponses({@ApiResponse(responseCode="204", description="Logout completed."), @ApiResponse(responseCode="401", description="A valid bearer token is required."), @ApiResponse(responseCode="500", description="Unexpected server error.")})
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt principal) {
        authService.logout(
                principal.getSubject(),
                principal.getClaimAsString("role"),
                tokenVersion(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change password", description = "Changes the authenticated account password after verifying the current password. Requires a valid bearer token.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Current password and new password (12 to 72 characters).", required = true, content = @io.swagger.v3.oas.annotations.media.Content(examples = @ExampleObject(value = "{\"currentPassword\":\"StrongPassword123!\",\"newPassword\":\"EvenStrongerPassword123!\"}"))))
    @ApiResponses({@ApiResponse(responseCode="204", description="Password changed."), @ApiResponse(responseCode="400", description="Request body is malformed, fails validation, or the new password is not accepted."), @ApiResponse(responseCode="401", description="A valid bearer token is required or the current password is invalid."), @ApiResponse(responseCode="500", description="Unexpected server error.")})
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
    @Operation(summary = "Request a password reset", description = "Accepts an account email and starts the password recovery flow. Returns a response that does not disclose whether the account exists.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Email address for the account.", required = true, content = @io.swagger.v3.oas.annotations.media.Content(examples = @ExampleObject(value = "{\"email\":\"applicant@example.com\"}"))))
    @ApiResponses({@ApiResponse(responseCode="202", description="Password reset request accepted."), @ApiResponse(responseCode="400", description="Request body is malformed or fails validation."), @ApiResponse(responseCode="500", description="Unexpected server error.")})
    public ResponseEntity<PasswordResetResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.accepted().body(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Sets a new password using a valid one-time reset token.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Reset token and new password (12 to 72 characters).", required = true, content = @io.swagger.v3.oas.annotations.media.Content(examples = @ExampleObject(value = "{\"resetToken\":\"reset-token-from-email\",\"newPassword\":\"NewStrongPassword123!\"}"))))
    @ApiResponses({@ApiResponse(responseCode="204", description="Password reset completed."), @ApiResponse(responseCode="400", description="Request body is malformed, fails validation, or the reset token is invalid."), @ApiResponse(responseCode="500", description="Unexpected server error.")})
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the current authenticated user", description = "Returns the account associated with the valid bearer token.")
    @ApiResponses({@ApiResponse(responseCode="200", description="Returns the current account details."), @ApiResponse(responseCode="401", description="A valid bearer token is required."), @ApiResponse(responseCode="500", description="Unexpected server error.")})
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
