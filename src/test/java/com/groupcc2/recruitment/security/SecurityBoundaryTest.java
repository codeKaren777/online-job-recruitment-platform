package com.groupcc2.recruitment.security;

import com.groupcc2.recruitment.application.service.ApplicationService;
import com.groupcc2.recruitment.auth.dto.CurrentUserResponse;
import com.groupcc2.recruitment.auth.dto.LoginResponse;
import com.groupcc2.recruitment.auth.dto.RegisterResponse;
import com.groupcc2.recruitment.auth.service.AuthService;
import com.groupcc2.recruitment.job.dto.JobResponse;
import com.groupcc2.recruitment.job.entity.JobStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    @MockitoBean ApplicationService applicationService;

    @Test
    void profileRoutesRejectRolesOtherThanJobSeeker() throws Exception {
        for (String role : new String[]{"EMPLOYER", "ADMIN"}) {
            var token = jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
            mvc.perform(get("/api/profile").with(token)).andExpect(status().isForbidden());
            mvc.perform(put("/api/profile").with(token).contentType("application/json")
                    .content("{\"displayName\":\"Name\"}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/profile/documents").with(token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/profile/documents/" + java.util.UUID.randomUUID() + "/download").with(token))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/profile/documents/" + java.util.UUID.randomUUID()).with(token)).andExpect(status().isForbidden());
        }
        org.mockito.Mockito.verifyNoInteractions(profileService);
    }

    @Test
    void jobsRoutesRequireExplicitRoleAuthorization() throws Exception {
        UUID jobId = UUID.randomUUID();
        var employerToken = jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("role", "EMPLOYER"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_EMPLOYER"));
        var seekerToken = jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("role", "JOB_SEEKER"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_JOB_SEEKER"));
        var adminToken = jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("role", "ADMIN"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
        when(jobService.createJob(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new JobResponse(jobId, UUID.randomUUID(), "Backend Engineer", "Build APIs",
                        "BSc CS", java.time.LocalDate.of(2099, 12, 31), "Zomba", JobStatus.ACTIVE,
                        java.time.Instant.now(), java.time.Instant.now()));
        when(jobService.getJob(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        when(jobService.updateJob(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        when(jobService.closeJob(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs").with(seekerToken)).andExpect(status().isOk());
        mvc.perform(get("/api/jobs").with(employerToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/jobs").with(adminToken)).andExpect(status().isForbidden());

        mvc.perform(post("/api/jobs").contentType("application/json")
                .content("""
                        {"title":"Backend Engineer","description":"Build APIs","requiredQualifications":"BSc CS","dueDate":"2099-12-31","location":"Zomba"}
                        """))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/jobs").with(seekerToken).contentType("application/json")
                .content("""
                        {"title":"Backend Engineer","description":"Build APIs","requiredQualifications":"BSc CS","dueDate":"2099-12-31","location":"Zomba"}
                        """))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/jobs").with(employerToken).contentType("application/json")
                .content("""
                        {"title":"Backend Engineer","description":"Build APIs","requiredQualifications":"BSc CS","dueDate":"2099-12-31","location":"Zomba"}
                        """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/jobs/{jobId}", jobId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs/{jobId}", jobId).with(adminToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/jobs/{jobId}", jobId).with(seekerToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/jobs/{jobId}", jobId).with(employerToken)).andExpect(status().isNotFound());

        mvc.perform(get("/api/employer/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/employer/jobs").with(seekerToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/employer/jobs").with(employerToken)).andExpect(status().isOk());

        mvc.perform(put("/api/jobs/{jobId}", jobId).contentType("application/json")
                .content("""
                        {"title":"Backend Engineer","description":"Build APIs","requiredQualifications":"BSc CS","dueDate":"2099-12-31","location":"Zomba"}
                        """))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/jobs/{jobId}", jobId).with(seekerToken).contentType("application/json")
                .content("""
                        {"title":"Backend Engineer","description":"Build APIs","requiredQualifications":"BSc CS","dueDate":"2099-12-31","location":"Zomba"}
                        """))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/jobs/{jobId}", jobId).with(employerToken).contentType("application/json")
                .content("""
                        {"title":"Backend Engineer","description":"Build APIs","requiredQualifications":"BSc CS","dueDate":"2099-12-31","location":"Zomba"}
                        """))
                .andExpect(status().isNotFound());

        mvc.perform(patch("/api/jobs/{jobId}/close", jobId)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/jobs/{jobId}/close", jobId).with(seekerToken)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/jobs/{jobId}/close", jobId).with(employerToken)).andExpect(status().isNotFound());
    }

    @Test
    void closedRoutesRejectAnonymousAndAuthenticatedRequests() throws Exception {
        for (String path : new String[] {"/api/jobs", "/api/profiles/me", "/api/applications", "/api/admin/users"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(jwt())).andExpect(status().isForbidden());
        }
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
