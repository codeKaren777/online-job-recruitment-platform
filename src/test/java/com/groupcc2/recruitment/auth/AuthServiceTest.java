package com.groupcc2.recruitment.auth;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import com.groupcc2.recruitment.auth.dto.CurrentUserResponse;
import com.groupcc2.recruitment.auth.dto.ChangePasswordRequest;
import com.groupcc2.recruitment.auth.dto.ForgotPasswordRequest;
import com.groupcc2.recruitment.auth.dto.LoginRequest;
import com.groupcc2.recruitment.auth.dto.LoginResponse;
import com.groupcc2.recruitment.auth.dto.PasswordResetRequest;
import com.groupcc2.recruitment.auth.dto.PasswordResetResponse;
import com.groupcc2.recruitment.auth.dto.RegisterRequest;
import com.groupcc2.recruitment.auth.dto.RegisterResponse;
import com.groupcc2.recruitment.users.entity.AccountStatus;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.auth.repository.AuthCredentialRepository;
import com.groupcc2.recruitment.auth.service.AuthService;
import com.groupcc2.recruitment.auth.service.EmailAlreadyRegisteredException;
import com.groupcc2.recruitment.auth.service.JwtTokenService;
import com.groupcc2.recruitment.auth.service.InvalidPasswordResetTokenException;
import com.groupcc2.recruitment.auth.service.PasswordHashingService;
import com.groupcc2.recruitment.auth.service.PasswordResetTokenExposure;
import com.groupcc2.recruitment.auth.service.PasswordResetTokenService;
import com.groupcc2.recruitment.security.RlsTransactionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.BadCredentialsException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final String RAW_PASSWORD = "StrongPassword123!";
    private static final String PASSWORD_HASH = "$2b$test-hash";

    @Mock private AuthCredentialRepository userRepository;
    @Mock private PasswordHashingService passwordHashingService;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private RlsTransactionContext rlsTransactionContext;
    @Mock private PasswordResetTokenService passwordResetTokenService;
    @Mock private PasswordResetTokenExposure passwordResetTokenExposure;
    @InjectMocks private AuthService authService;

    @Test
    void registrationRejectsAdminEvenWhenRequestValidationWasBypassed() {
        RegisterRequest request = new RegisterRequest("person@example.com", RAW_PASSWORD, UserRole.ADMIN);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(passwordHashingService, never()).hash(RAW_PASSWORD);
        verify(userRepository, never()).registerUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void registrationHashesPasswordAndReturnsSafeAccountDetails() {
        UUID userId = UUID.randomUUID();
        AuthCredentialRepository.RegistrationResult stored = org.mockito.Mockito.mock(AuthCredentialRepository.RegistrationResult.class);
        when(stored.getId()).thenReturn(userId);
        when(stored.getEmail()).thenReturn("person@example.com");
        when(stored.getRole()).thenReturn("JOB_SEEKER");
        when(stored.getStatus()).thenReturn("ACTIVE");
        when(passwordHashingService.hash(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        when(userRepository.registerUser("person@example.com", PASSWORD_HASH, "JOB_SEEKER"))
                .thenReturn(Optional.of(stored));

        RegisterResponse response = authService.register(
                new RegisterRequest("person@example.com", RAW_PASSWORD, UserRole.JOB_SEEKER));

        assertThat(response).isEqualTo(new RegisterResponse(
                userId, "person@example.com", UserRole.JOB_SEEKER, AccountStatus.ACTIVE));
        verify(userRepository).registerUser("person@example.com", PASSWORD_HASH, "JOB_SEEKER");
    }

    @Test
    void registrationMapsUniqueEmailConflictToConflictWithoutPrechecking() {
        when(passwordHashingService.hash(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        when(userRepository.registerUser("person@example.com", PASSWORD_HASH, "EMPLOYER"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("person@example.com", RAW_PASSWORD, UserRole.EMPLOYER)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void registrationDoesNotHideUnexpectedDatabaseErrors() {
        when(passwordHashingService.hash(RAW_PASSWORD)).thenReturn(PASSWORD_HASH);
        doThrow(new IllegalStateException("database unavailable"))
                .when(userRepository).registerUser("person@example.com", PASSWORD_HASH, "JOB_SEEKER");

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("person@example.com", RAW_PASSWORD, UserRole.JOB_SEEKER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    @Test
    void loginVerifiesPasswordAndActiveStatusBeforeIssuingToken() {
        UUID userId = UUID.randomUUID();
        AuthCredentialRepository.LoginCredential credentials = credentials(
                userId, PASSWORD_HASH, "EMPLOYER", "ACTIVE");
        when(userRepository.findLoginCredentialsByEmail("person@example.com"))
                .thenReturn(Optional.of(credentials));
        when(passwordHashingService.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(jwtTokenService.generateAccessToken(userId, UserRole.EMPLOYER, 0)).thenReturn("signed-token");

        LoginResponse response = authService.login(new LoginRequest("person@example.com", RAW_PASSWORD));

        assertThat(response).isEqualTo(new LoginResponse(
                "signed-token", "Bearer", JwtTokenService.ACCESS_TOKEN_LIFETIME_SECONDS));
        verify(jwtTokenService).generateAccessToken(userId, UserRole.EMPLOYER, 0);
    }

    @Test
    void loginRejectsUnknownEmailsAndIncorrectPasswords() {
        when(userRepository.findLoginCredentialsByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", RAW_PASSWORD)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(passwordHashingService).performDummyMatch(RAW_PASSWORD);

        AuthCredentialRepository.LoginCredential credential = credentials(
                UUID.randomUUID(), PASSWORD_HASH, "JOB_SEEKER", "ACTIVE");
        when(userRepository.findLoginCredentialsByEmail("person@example.com"))
                .thenReturn(Optional.of(credential));
        when(passwordHashingService.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);
        assertThatThrownBy(() -> authService.login(new LoginRequest("person@example.com", RAW_PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtTokenService, never()).generateAccessToken(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginRejectsSuspendedAndRemovedAccounts() {
        when(passwordHashingService.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        for (AccountStatus status : new AccountStatus[] {AccountStatus.SUSPENDED, AccountStatus.REMOVED}) {
            String email = status.name().toLowerCase() + "@example.com";
            AuthCredentialRepository.LoginCredential credential = credentials(
                    UUID.randomUUID(), PASSWORD_HASH, "JOB_SEEKER", status.name());
            when(userRepository.findLoginCredentialsByEmail(email))
                    .thenReturn(Optional.of(credential));

            assertThatThrownBy(() -> authService.login(new LoginRequest(email, RAW_PASSWORD)))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }
        verify(jwtTokenService, never()).generateAccessToken(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void currentUserUsesVerifiedIdentityAndSafeResponse() {
        UUID userId = UUID.randomUUID();
        CurrentUserResponse expected = new CurrentUserResponse(
                userId, "person@example.com", UserRole.JOB_SEEKER, AccountStatus.ACTIVE);
        when(userRepository.findCurrentUserById(userId)).thenReturn(Optional.of(expected));

        assertThat(authService.currentUser(userId.toString(), UserRole.JOB_SEEKER.name()))
                .isEqualTo(expected);
        verify(rlsTransactionContext).setAuthenticatedUser(userId, UserRole.JOB_SEEKER);
    }

    @Test
    void logoutRevokesAllTokensAtCurrentVersion() {
        UUID userId = UUID.randomUUID();
        when(userRepository.revokeSessions(userId, 3)).thenReturn(1);

        authService.logout(userId.toString(), UserRole.JOB_SEEKER.name(), 3);

        verify(userRepository).revokeSessions(userId, 3);
    }

    @Test
    void passwordChangeVerifiesCurrentPasswordAndRotatesTokenVersion() {
        UUID userId = UUID.randomUUID();
        AuthCredentialRepository.LoginCredential credential =
                credentials(userId, PASSWORD_HASH, "JOB_SEEKER", "ACTIVE", 2);
        when(userRepository.findLoginCredentialsById(userId)).thenReturn(Optional.of(credential));
        when(passwordHashingService.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(passwordHashingService.hash("NewStrongPassword123!")).thenReturn("new-hash");
        when(userRepository.changePassword(userId, PASSWORD_HASH, "new-hash", 2)).thenReturn(1);

        authService.changePassword(userId.toString(), "JOB_SEEKER", 2,
                new ChangePasswordRequest(RAW_PASSWORD, "NewStrongPassword123!"));

        verify(userRepository).changePassword(userId, PASSWORD_HASH, "new-hash", 2);
    }

    @Test
    void passwordChangeRejectsIncorrectCurrentPassword() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findLoginCredentialsById(userId)).thenReturn(Optional.of(
                credentials(userId, PASSWORD_HASH, "JOB_SEEKER", "ACTIVE", 0)));
        when(passwordHashingService.matches("wrong-current", PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(userId.toString(), "JOB_SEEKER", 0,
                new ChangePasswordRequest("wrong-current", "NewStrongPassword123!")))
                .isInstanceOf(BadCredentialsException.class);
        verify(userRepository, never()).changePassword(
                org.mockito.ArgumentMatchers.any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void forgotPasswordReturnsGenericMessageAndOnlyLocalTokenForKnownAccount() {
        when(passwordResetTokenService.generateToken()).thenReturn("generated-reset-token");
        when(passwordResetTokenService.hashToken("generated-reset-token")).thenReturn("sha256-hash");
        when(passwordResetTokenExposure.exposeForLocalDevelopment("generated-reset-token"))
                .thenReturn(Optional.of("generated-reset-token"));
        when(userRepository.storePasswordResetToken(
                org.mockito.ArgumentMatchers.eq("person@example.com"),
                org.mockito.ArgumentMatchers.eq("sha256-hash"),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(1);

        PasswordResetResponse known = authService.forgotPassword(new ForgotPasswordRequest("person@example.com"));

        assertThat(known.message()).isEqualTo(
                "If the account exists, the reset request has been accepted.");
        assertThat(known.resetToken()).isEqualTo("generated-reset-token");

        when(userRepository.storePasswordResetToken(
                org.mockito.ArgumentMatchers.eq("missing@example.com"),
                org.mockito.ArgumentMatchers.eq("sha256-hash"),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(0);
        PasswordResetResponse missing = authService.forgotPassword(new ForgotPasswordRequest("missing@example.com"));
        assertThat(missing.message()).isEqualTo(known.message());
        assertThat(missing.resetToken()).isNull();
        verify(passwordResetTokenExposure, never()).exposeForLocalDevelopment(
                org.mockito.ArgumentMatchers.argThat(token -> !"generated-reset-token".equals(token)));
    }

    @Test
    void passwordResetConsumesTokenAndChangesPasswordOrRejectsUnusableToken() {
        when(passwordResetTokenService.hashToken("valid-reset-token")).thenReturn("sha256-hash");
        when(passwordHashingService.hash("NewStrongPassword123!")).thenReturn("new-hash");
        when(userRepository.resetPassword("sha256-hash", "new-hash")).thenReturn(1);

        authService.resetPassword(new PasswordResetRequest("valid-reset-token", "NewStrongPassword123!"));

        verify(userRepository).resetPassword("sha256-hash", "new-hash");
        when(userRepository.resetPassword("sha256-hash", "new-hash")).thenReturn(0);
        assertThatThrownBy(() -> authService.resetPassword(
                new PasswordResetRequest("valid-reset-token", "NewStrongPassword123!")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void currentUserRejectsMalformedSubjectAndStaleOrInactiveAccounts() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> authService.currentUser("not-a-uuid", UserRole.JOB_SEEKER.name()))
                .isInstanceOf(BadCredentialsException.class);
        verify(rlsTransactionContext, never())
                .setAuthenticatedUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        when(userRepository.findCurrentUserById(userId)).thenReturn(Optional.of(new CurrentUserResponse(
                userId, "person@example.com", UserRole.EMPLOYER, AccountStatus.ACTIVE)));
        assertThatThrownBy(() -> authService.currentUser(userId.toString(), UserRole.JOB_SEEKER.name()))
                .isInstanceOf(BadCredentialsException.class);

        when(userRepository.findCurrentUserById(userId)).thenReturn(Optional.of(new CurrentUserResponse(
                userId, "person@example.com", UserRole.JOB_SEEKER, AccountStatus.SUSPENDED)));
        assertThatThrownBy(() -> authService.currentUser(userId.toString(), UserRole.JOB_SEEKER.name()))
                .isInstanceOf(BadCredentialsException.class);
    }

    private AuthCredentialRepository.LoginCredential credentials(
            UUID id, String passwordHash, String role, String status) {
        return credentials(id, passwordHash, role, status, 0);
    }

    private AuthCredentialRepository.LoginCredential credentials(
            UUID id, String passwordHash, String role, String status, int tokenVersion) {
        return new TestLoginCredential(id, passwordHash, role, status, tokenVersion);
    }

    private static class TestLoginCredential implements AuthCredentialRepository.LoginCredential {
        private final UUID id;
        private final String passwordHash;
        private final String role;
        private final String status;
        private final int tokenVersion;

        private TestLoginCredential(UUID id, String passwordHash, String role, String status, int tokenVersion) {
            this.id = id;
            this.passwordHash = passwordHash;
            this.role = role;
            this.status = status;
            this.tokenVersion = tokenVersion;
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public String getPasswordHash() {
            return passwordHash;
        }

        @Override
        public String getRole() {
            return role;
        }

        @Override
        public String getStatus() {
            return status;
        }

        @Override
        public int getTokenVersion() {
            return tokenVersion;
        }
    }
}
