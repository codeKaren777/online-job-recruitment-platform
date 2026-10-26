package com.groupcc2.recruitment.application;

import com.groupcc2.recruitment.application.controller.EmployerApplicationController;
import com.groupcc2.recruitment.application.dto.ApplicationResponse;
import com.groupcc2.recruitment.application.entity.ApplicationStatus;
import com.groupcc2.recruitment.application.service.ApplicationService;
import com.groupcc2.recruitment.security.AccountJwtValidator;
import com.groupcc2.recruitment.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmployerApplicationController.class,
        properties = "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import(SecurityConfig.class)
class EmployerApplicationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ApplicationService service;
    @MockitoBean AccountJwtValidator accountJwtValidator;

    private RequestPostProcessor employerToken(UUID id) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_EMPLOYER"))
                .jwt(token -> token.subject(id.toString()));
    }

    @Test
    void employerCanListOwnJobApplications() throws Exception {
        UUID employerId = UUID.randomUUID();
        ApplicationResponse response = new ApplicationResponse(
                UUID.randomUUID(), UUID.randomUUID(), ApplicationStatus.PENDING,
                Instant.now(), Instant.now(), null, null, null);
        when(service.listForEmployer(employerId, null, null)).thenReturn(List.of(response));

        mvc.perform(get("/api/employer/applications").with(employerToken(employerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        verify(service).listForEmployer(employerId, null, null);
    }

    @Test
    void jobSeekersCannotReachEmployerApplicationRoutes() throws Exception {
        UUID seekerId = UUID.randomUUID();
        mvc.perform(get("/api/employer/applications").with(
                        jwt().authorities(new SimpleGrantedAuthority("ROLE_JOB_SEEKER"))
                                .jwt(token -> token.subject(seekerId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void employerCanShortlistAnApplication() throws Exception {
        UUID employerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        ApplicationResponse response = new ApplicationResponse(
                applicationId, UUID.randomUUID(), ApplicationStatus.SHORTLISTED,
                Instant.now(), Instant.now(), Instant.now(), null, null);
        when(service.changeStatus(employerId, applicationId, ApplicationStatus.SHORTLISTED)).thenReturn(response);

        mvc.perform(patch("/api/employer/applications/{id}/status", applicationId)
                        .with(employerToken(employerId))
                        .contentType("application/json")
                        .content("{\"status\":\"SHORTLISTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHORTLISTED"));

        verify(service).changeStatus(employerId, applicationId, ApplicationStatus.SHORTLISTED);
    }

    @Test
    void jobSeekersCannotChangeApplicationStatus() throws Exception {
        mvc.perform(patch("/api/employer/applications/{id}/status", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JOB_SEEKER")))
                        .contentType("application/json")
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());
    }
}
