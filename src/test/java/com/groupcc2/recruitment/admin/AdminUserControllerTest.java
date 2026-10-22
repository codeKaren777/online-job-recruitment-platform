package com.groupcc2.recruitment.admin;

import com.groupcc2.recruitment.admin.controller.AdminUserController;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.groupcc2.recruitment.security.AccountJwtValidator;
import com.groupcc2.recruitment.security.SecurityConfig;
import com.groupcc2.recruitment.auth.service.AuthService;
import com.groupcc2.recruitment.job.service.JobService;
import com.groupcc2.recruitment.profile.service.ProfileService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class AdminUserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService users;
    @MockitoBean AccountJwtValidator accountJwtValidator;
    @MockitoBean AuthService authService;
    @MockitoBean JobService jobService;
    @MockitoBean ProfileService profileService;

    @Test
    void administratorCanListUsersThroughTheUserService() throws Exception {
        UUID administratorId = UUID.randomUUID();
        User user = new User("person@example.com", "hash", UserRole.JOB_SEEKER);
        when(users.findAllForAdministrator(administratorId)).thenReturn(List.of(user));

        mvc.perform(get("/api/admin/users").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        .jwt(token -> token.subject(administratorId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("person@example.com"));

        verify(users).findAllForAdministrator(administratorId);
    }

    @Test
    void administratorCanSuspendAndRemoveUsers() throws Exception {
        UUID administratorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User user = new User("person@example.com", "hash", UserRole.JOB_SEEKER);
        when(users.findForAdministrator(administratorId, userId)).thenReturn(user);

        mvc.perform(patch("/api/admin/users/{id}/suspend", userId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                                .jwt(token -> token.subject(administratorId.toString()))))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/admin/users/{id}/remove", userId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                                .jwt(token -> token.subject(administratorId.toString()))))
                .andExpect(status().isOk());

        verify(users).suspend(administratorId, userId);
        verify(users).remove(administratorId, userId);
    }
}
