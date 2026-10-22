package com.groupcc2.recruitment.users;

import com.groupcc2.recruitment.users.controller.UserController;
import com.groupcc2.recruitment.application.service.ApplicationService;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class UserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService users;
    @MockitoBean AccountJwtValidator accountJwtValidator;
    @MockitoBean AuthService authService;
    @MockitoBean JobService jobService;
    @MockitoBean ProfileService profileService;
    @MockitoBean ApplicationService applicationService;

    @Test
    void currentUserUsesTheAuthenticatedPrincipalSubject() throws Exception {
        UUID id = UUID.randomUUID();
        User user = new User("person@example.com", "hash", UserRole.JOB_SEEKER);
        when(users.findById(id)).thenReturn(user);

        mvc.perform(get("/api/users/me").with(jwt().jwt(token -> token.subject(id.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("person@example.com"))
                .andExpect(jsonPath("$.role").value("JOB_SEEKER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(users).findById(eq(id));
    }
}
