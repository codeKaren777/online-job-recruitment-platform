package com.groupcc2.recruitment.security;

import com.groupcc2.recruitment.auth.dto.CurrentUserResponse;
import com.groupcc2.recruitment.auth.dto.LoginResponse;
import com.groupcc2.recruitment.auth.dto.RegisterResponse;
import com.groupcc2.recruitment.auth.service.AuthService;
import com.groupcc2.recruitment.job.service.JobService;
import com.groupcc2.recruitment.profile.service.ProfileService;
import com.groupcc2.recruitment.users.entity.AccountStatus;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import(SecurityConfig.class)
class SecurityBoundaryTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService userService;
    @MockitoBean JobService jobService;
    @MockitoBean ProfileService profileService;
    @MockitoBean AuthService authService;
    @MockitoBean AccountJwtValidator accountJwtValidator;

    @Test
    void profileIntegrationGateRejectsRolesOtherThanJobSeekers() throws Exception {
        for (String role : new String[]{"EMPLOYER", "ADMIN"}) {
            var token = jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
            mvc.perform(get("/api/profile").with(token)).andExpect(status().isForbidden());
            mvc.perform(put("/api/profile").with(token).contentType("application/json")
                    .content("{\"displayName\":\"Name\"}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/profile/documents").with(token)).andExpect(status().isForbidden());
            mvc.perform(delete("/api/profile/documents/" + java.util.UUID.randomUUID()).with(token)).andExpect(status().isForbidden());
        }
        org.mockito.Mockito.verifyNoInteractions(profileService);
    }

    @Test
    void closedRoutesRejectAnonymousAndAuthenticatedRequests() throws Exception {
        doThrow(new com.groupcc2.recruitment.users.service.AdminRegistrationNotAllowedException())
                .when(userService).register("admin@example.com", "correct horse battery",
                        com.groupcc2.recruitment.users.entity.UserRole.ADMIN);
        for (String path : new String[] {"/api/profile", "/api/profile/documents", "/api/jobs", "/api/profiles/me", "/api/applications", "/api/admin/users"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(jwt())).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/users/register").contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"password\":\"correct horse battery\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationAndLoginAreDeliberatelyPublicButOtherFeatureRoutesRemainClosed() throws Exception {
        UUID userId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(
                new com.groupcc2.recruitment.auth.service.InvalidPasswordResetTokenException())
                .when(authService).resetPassword(org.mockito.ArgumentMatchers.any());
        when(authService.register(org.mockito.ArgumentMatchers.any())).thenReturn(
                new RegisterResponse(userId, "person@example.com",
                        UserRole.JOB_SEEKER, AccountStatus.ACTIVE));
        when(authService.login(org.mockito.ArgumentMatchers.any())).thenReturn(
                new LoginResponse("test-token", "Bearer", 1800));
        when(authService.currentUser(userId.toString(), UserRole.JOB_SEEKER.name()))
                .thenReturn(new CurrentUserResponse(
                        userId, "person@example.com", UserRole.JOB_SEEKER, AccountStatus.ACTIVE));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").with(jwt().jwt(token -> token
                        .subject(userId.toString()).claim("role", UserRole.JOB_SEEKER.name()))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/change-password")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").with(jwt().jwt(token -> token
                        .subject(userId.toString()).claim("role", UserRole.JOB_SEEKER.name())
                        .claim("tokenVersion", 0))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/change-password").with(jwt().jwt(token -> token
                        .subject(userId.toString()).claim("role", UserRole.JOB_SEEKER.name())
                        .claim("tokenVersion", 0)))
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"CurrentPassword123!","newPassword":"NewStrongPassword123!"}
                                """))
                .andExpect(status().isNoContent());

        for (String path : new String[] {"/api/jobs", "/api/profiles/me",
                "/api/applications", "/api/admin/users"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(jwt())).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("""
                        {"email":"person@example.com","password":"StrongPassword123!","role":"JOB_SEEKER"}
                        """)).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"person@example.com\",\"password\":\"StrongPassword123!\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"person@example.com\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content("{\"resetToken\":\"invalid-but-well-formed-token\",\"newPassword\":\"StrongPassword123!\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("""
                        {"email":"admin@example.com","password":"StrongPassword123!","role":"ADMIN"}
                        """)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/jobs").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
}
