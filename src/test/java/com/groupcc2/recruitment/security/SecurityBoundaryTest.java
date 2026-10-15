package com.groupcc2.recruitment.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.groupcc2.recruitment.users.service.UserService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import(SecurityConfig.class)
class SecurityBoundaryTest {
    @Autowired MockMvc mvc;
    @MockBean UserService userService;
    @MockBean
    private com.groupcc2.recruitment.job.service.JobService jobService;
    @Test
    void closedRoutesRejectAnonymousAndAuthenticatedRequests() throws Exception {
        doThrow(new com.groupcc2.recruitment.users.service.AdminRegistrationNotAllowedException())
                .when(userService).register("admin@example.com", "correct horse battery",
                        com.groupcc2.recruitment.users.entity.UserRole.ADMIN);
        for (String path : new String[] {"/api/jobs", "/api/profiles/me", "/api/applications", "/api/admin/users"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(jwt())).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/users/register").contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"password\":\"correct horse battery\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/jobs").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
}
