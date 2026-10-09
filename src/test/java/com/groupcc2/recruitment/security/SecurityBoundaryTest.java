package com.groupcc2.recruitment.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import(SecurityConfig.class)
class SecurityBoundaryTest {
    @Autowired MockMvc mvc;

    @Test
    void closedRoutesRejectAnonymousAndAuthenticatedRequests() throws Exception {
        for (String path : new String[] {"/api/jobs", "/api/profiles/me", "/api/applications", "/api/admin/users"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(jwt())).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"role\":\"ADMIN\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
}
