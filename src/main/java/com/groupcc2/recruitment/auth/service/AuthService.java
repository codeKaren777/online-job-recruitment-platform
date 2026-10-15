package com.groupcc2.recruitment.auth.service;

import java.util.UUID;
import java.time.Instant;

import com.groupcc2.recruitment.auth.dto.ChangePasswordRequest;
import com.groupcc2.recruitment.auth.dto.CurrentUserResponse;
import com.groupcc2.recruitment.auth.dto.ForgotPasswordRequest;
import com.groupcc2.recruitment.auth.dto.LoginRequest;
import com.groupcc2.recruitment.auth.dto.LoginResponse;
import com.groupcc2.recruitment.auth.dto.PasswordResetRequest;
import com.groupcc2.recruitment.auth.dto.PasswordResetResponse;
import com.groupcc2.recruitment.auth.dto.RegisterRequest;
import com.groupcc2.recruitment.auth.dto.RegisterResponse;
import com.groupcc2.recruitment.auth.entity.AccountStatus;
import com.groupcc2.recruitment.auth.entity.UserRole;
import com.groupcc2.recruitment.auth.repository.UserRepository;
import com.groupcc2.recruitment.security.RlsTransactionContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String RESET_MESSAGE =
            "If the account exists, the reset request has been accepted.";
    private static final long RESET_TOKEN_LIFETIME_SECONDS = 900;

    private final UserRepository userRepository;
    private final PasswordHashingService passwordHashingService;
    private final JwtTokenService jwtTokenService;
    private final RlsTransactionContext rlsTransactionContext;
    private final PasswordResetTokenService passwordResetTokenService;
    private final PasswordResetTokenExposure passwordResetTokenExposure;

    public AuthService(UserRepository userRepository, PasswordHashingService passwordHashingService,
                       JwtTokenService jwtTokenService, RlsTransactionContext rlsTransactionContext,
                       PasswordResetTokenService passwordResetTokenService,
                       PasswordResetTokenExposure passwordResetTokenExposure) {
        this.userRepository = userRepository;
        this.passwordHashingService = passwordHashingService;
        this.jwtTokenService = jwtTokenService;
        this.rlsTransactionContext = rlsTransactionContext;
        this.passwordResetTokenService = passwordResetTokenService;
        this.passwordResetTokenExposure = passwordResetTokenExposure;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (request.role() != UserRole.JOB_SEEKER && request.role() != UserRole.EMPLOYER) {
            throw new IllegalArgumentException("Public registration is limited to JOB_SEEKER and EMPLOYER");
        }
        String passwordHash = passwordHashingService.hash(request.password());
        return userRepository.registerUser(request.email(), passwordHash, request.role().name())
                .map(user -> new RegisterResponse(user.getId(), user.getEmail(),
                        UserRole.valueOf(user.getRole()), AccountStatus.valueOf(user.getStatus())))
                .orElseThrow(EmailAlreadyRegisteredException::new);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserRepository.LoginCredential credentials = userRepository.findLoginCredentialsByEmail(request.email())
                .orElse(null);
        if (credentials == null) {
            passwordHashingService.performDummyMatch(request.password());
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        UserRole role = UserRole.valueOf(credentials.getRole());
        if (!passwordHashingService.matches(request.password(), credentials.getPasswordHash())
                || !AccountStatus.ACTIVE.name().equals(credentials.getStatus())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        String accessToken = jwtTokenService.generateAccessToken(
                credentials.getId(), role, credentials.getTokenVersion());
        return new LoginResponse(accessToken, "Bearer", JwtTokenService.ACCESS_TOKEN_LIFETIME_SECONDS);
    }

    @Transactional
    public void logout(String subject, String roleClaim, int tokenVersion) {
        UUID userId = parseUserId(subject, roleClaim);
        if (tokenVersion < 0 || userRepository.revokeSessions(userId, tokenVersion) != 1) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
    }

    @Transactional
    public void changePassword(String subject, String roleClaim, int tokenVersion, ChangePasswordRequest request) {
        UUID userId = parseUserId(subject, roleClaim);
        UserRepository.LoginCredential credentials = userRepository.findLoginCredentialsById(userId)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
        if (credentials.getTokenVersion() != tokenVersion
                || !AccountStatus.ACTIVE.name().equals(credentials.getStatus())
                || !UserRole.valueOf(credentials.getRole()).name().equals(roleClaim)
                || !passwordHashingService.matches(request.currentPassword(), credentials.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
        if (userRepository.changePassword(
                userId,
                credentials.getPasswordHash(),
                passwordHashingService.hash(request.newPassword()),
                tokenVersion) != 1) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
    }

    @Transactional
    public PasswordResetResponse forgotPassword(ForgotPasswordRequest request) {
        String rawToken = passwordResetTokenService.generateToken();
        String hashedToken = passwordResetTokenService.hashToken(rawToken);
        int updated = userRepository.storePasswordResetToken(
                request.email(), hashedToken, Instant.now().plusSeconds(RESET_TOKEN_LIFETIME_SECONDS));
        String exposedToken = updated == 1
                ? passwordResetTokenExposure.exposeForLocalDevelopment(rawToken).orElse(null)
                : null;
        return new PasswordResetResponse(RESET_MESSAGE, exposedToken);
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        String tokenHash = passwordResetTokenService.hashToken(request.resetToken());
        String passwordHash = passwordHashingService.hash(request.newPassword());
        if (userRepository.resetPassword(tokenHash, passwordHash) != 1) {
            throw new InvalidPasswordResetTokenException();
        }
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(String subject, String roleClaim) {
        UUID userId = parseUserId(subject, roleClaim);
        UserRole tokenRole = UserRole.valueOf(roleClaim);

        rlsTransactionContext.setAuthenticatedUser(userId, tokenRole);
        CurrentUserResponse currentUser = userRepository.findCurrentUserById(userId)
                .filter(user -> user.status() == AccountStatus.ACTIVE && user.role() == tokenRole)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
        return currentUser;
    }

    private UUID parseUserId(String subject, String roleClaim) {
        try {
            UserRole.valueOf(roleClaim);
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
    }
}
