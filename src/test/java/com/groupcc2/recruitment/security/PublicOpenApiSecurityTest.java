package com.groupcc2.recruitment.security;

import java.util.UUID;

import com.groupcc2.recruitment.application.service.ApplicationService;
import com.groupcc2.recruitment.auth.service.AuthService;
import com.groupcc2.recruitment.common.openapi.OpenApiConfiguration;
import com.groupcc2.recruitment.job.service.JobService;
import com.groupcc2.recruitment.profile.service.ProfileService;
import com.groupcc2.recruitment.auth.dto.CurrentUserResponse;
import com.groupcc2.recruitment.users.entity.AccountStatus;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.UserService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(properties = {
        "app.security.jwt.secret-base64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "app.openapi.enabled=true",
        "app.openapi.public=true",
        "app.openapi.public-in-local=false",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@ActiveProfiles("prod")
@Import({SecurityConfig.class, OpenApiConfiguration.class, ProductionSecurityConfiguration.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class, SwaggerConfig.class})
@EnableConfigurationProperties({
        SpringDocConfigProperties.class, SwaggerUiConfigProperties.class, SwaggerUiOAuthProperties.class})
class PublicOpenApiSecurityTest {
    @MockitoBean com.groupcc2.recruitment.company.service.CompanyService companyService;
    @MockitoBean com.groupcc2.recruitment.users.employer.service.EmployerUpgradeService employerUpgradeService;
    @Autowired MockMvc mvc;
    @MockitoBean UserService userService;
    @MockitoBean JobService jobService;
    @MockitoBean ProfileService profileService;
    @MockitoBean AuthService authService;
    @MockitoBean AccountJwtValidator accountJwtValidator;
    @MockitoBean ApplicationService applicationService;

    @Test
    void productionPublicOpenApiDoesNotOpenBusinessEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].bearerAuth").exists())
                .andExpect(content().string(not(containsString("JWT_SECRET_BASE64"))))
                .andExpect(content().string(not(containsString("SPRING_MAIL_PASSWORD"))))
                .andExpect(content().string(not(containsString("PROFILE_STORAGE_SECRET_KEY"))));
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/employer/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JOB_SEEKER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtStillWorksForAuthenticatedBusinessRoute() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.currentUser(userId.toString(), UserRole.JOB_SEEKER.name()))
                .thenReturn(new CurrentUserResponse(
                        userId, "applicant@example.test", UserRole.JOB_SEEKER, AccountStatus.ACTIVE));

        mvc.perform(get("/api/auth/me").with(jwt().jwt(token -> token
                        .subject(userId.toString())
                        .claim("role", UserRole.JOB_SEEKER.name()))
                        .authorities(new SimpleGrantedAuthority("ROLE_JOB_SEEKER"))))
                .andExpect(status().isOk());
    }
}
