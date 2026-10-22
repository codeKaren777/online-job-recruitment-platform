package com.groupcc2.recruitment.security;

import com.groupcc2.recruitment.application.service.ApplicationService;
import com.groupcc2.recruitment.auth.service.AuthService;
import com.groupcc2.recruitment.common.openapi.OpenApiConfiguration;
import com.groupcc2.recruitment.job.service.JobService;
import com.groupcc2.recruitment.profile.service.ProfileService;
import com.groupcc2.recruitment.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
        "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.openapi.enabled=false",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@Import({SecurityConfig.class, OpenApiConfiguration.class})
class OpenApiDisabledTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService userService;
    @MockitoBean JobService jobService;
    @MockitoBean ProfileService profileService;
    @MockitoBean AuthService authService;
    @MockitoBean AccountJwtValidator accountJwtValidator;
    @MockitoBean ApplicationService applicationService;

    @Test
    void disabledDocumentationEndpointsAreNotPublicOrAvailable() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }
}
