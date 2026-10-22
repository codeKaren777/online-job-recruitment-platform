package com.groupcc2.recruitment.auth;

import java.util.UUID;

import com.groupcc2.recruitment.auth.controller.AuthController;
import com.groupcc2.recruitment.application.service.ApplicationService;
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
import com.groupcc2.recruitment.auth.service.AuthService;
import com.groupcc2.recruitment.auth.service.EmailAlreadyRegisteredException;
import com.groupcc2.recruitment.auth.service.InvalidPasswordResetTokenException;
import com.groupcc2.recruitment.auth.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ApplicationService applicationService;

    @Test
    void registrationValidatesRequestAndReturnsCreatedSafeResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.register(new RegisterRequest(
                "person@example.com", "StrongPassword123!", UserRole.JOB_SEEKER)))
                .thenReturn(new RegisterResponse(
                        userId, "person@example.com", UserRole.JOB_SEEKER, AccountStatus.ACTIVE));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"person@example.com","password":"StrongPassword123!","role":"JOB_SEEKER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("person@example.com"))
                .andExpect(jsonPath("$.role").value("JOB_SEEKER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(authService).register(new RegisterRequest(
                "person@example.com", "StrongPassword123!", UserRole.JOB_SEEKER));
    }

    @Test
    void registrationRejectsInvalidInputBeforeCallingService() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid","password":"short","role":"ADMIN"}
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        when(authService.register(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new EmailAlreadyRegisteredException());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"person@example.com","password":"StrongPassword123!","role":"JOB_SEEKER"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginValidatesRequestAndReturnsTokenResponse() throws Exception {
        when(authService.login(new LoginRequest("person@example.com", "StrongPassword123!")))
                .thenReturn(new LoginResponse(
                        "signed-token", "Bearer", JwtTokenService.ACCESS_TOKEN_LIFETIME_SECONDS));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"person@example.com","password":"StrongPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800));

        verify(authService).login(new LoginRequest("person@example.com", "StrongPassword123!"));
    }

    @Test
    void loginRejectsInvalidInputBeforeCallingService() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid","password":""}
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void currentUserUsesVerifiedJwtIdentityAndReturnsSafeResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.currentUser(userId.toString(), "JOB_SEEKER"))
                .thenReturn(new CurrentUserResponse(
                        userId, "person@example.com", UserRole.JOB_SEEKER, AccountStatus.ACTIVE));

        Jwt principal = Jwt.withTokenValue("controller-test")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("role", "JOB_SEEKER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("tokenVersion", 0)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(principal));
        try {
            mvc.perform(get("/api/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId.toString()))
                    .andExpect(jsonPath("$.email").value("person@example.com"))
                    .andExpect(jsonPath("$.role").value("JOB_SEEKER"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(authService).currentUser(userId.toString(), "JOB_SEEKER");
    }

    @Test
    void forgotPasswordReturnsAcceptedGenericResponseAndResetDoesNotReturnSecrets() throws Exception {
        when(authService.forgotPassword(new ForgotPasswordRequest("person@example.com")))
                .thenReturn(new PasswordResetResponse(
                        "If the account exists, the reset request has been accepted.", null));

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "If the account exists, the reset request has been accepted."))
                .andExpect(jsonPath("$.resetToken").doesNotExist());

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetToken":"RANDOM-RESET-TOKEN","newPassword":"NewStrongPassword123!"}
                                """))
                .andExpect(status().isNoContent());

        verify(authService).resetPassword(
                new PasswordResetRequest("RANDOM-RESET-TOKEN", "NewStrongPassword123!"));
    }

    @Test
    void invalidPasswordResetTokenReturnsBadRequest() throws Exception {
        org.mockito.Mockito.doThrow(new InvalidPasswordResetTokenException())
                .when(authService).resetPassword(org.mockito.ArgumentMatchers.any());

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetToken":"RANDOM-RESET-TOKEN","newPassword":"NewStrongPassword123!"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutAndChangePasswordUseIdentityAndVersionFromJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        Jwt principal = Jwt.withTokenValue("controller-test")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("role", "JOB_SEEKER")
                .claim("tokenVersion", 4)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(principal));
        try {
            mvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isNoContent());
            mvc.perform(post("/api/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"CurrentPassword123!","newPassword":"NewStrongPassword123!"}
                                    """))
                    .andExpect(status().isNoContent());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(authService).logout(userId.toString(), "JOB_SEEKER", 4);
        verify(authService).changePassword(userId.toString(), "JOB_SEEKER", 4,
                new ChangePasswordRequest("CurrentPassword123!", "NewStrongPassword123!"));
    }
}
