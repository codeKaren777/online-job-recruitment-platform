package com.groupcc2.recruitment.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.groupcc2.recruitment.application.controller.ApplicationController;
import com.groupcc2.recruitment.application.dto.ApplicationResponse;
import com.groupcc2.recruitment.application.entity.ApplicationStatus;
import com.groupcc2.recruitment.application.service.ApplicationFailure;
import com.groupcc2.recruitment.application.service.ApplicationService;
import com.groupcc2.recruitment.security.SecurityConfig;
import com.groupcc2.recruitment.security.AccountJwtValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApplicationController.class,
        properties = "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import(SecurityConfig.class)
class ApplicationControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ApplicationService service;
    @MockBean AccountJwtValidator accountJwtValidator;

    private final UUID userId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    private ApplicationResponse sample() {
        return new ApplicationResponse(UUID.randomUUID(), jobId, ApplicationStatus.PENDING,
                Instant.now(), Instant.now(), null, null, null);
    }

    private RequestPostProcessor seekerToken(UUID id) {
        return seekerSubject(id.toString());
    }

    private RequestPostProcessor seekerSubject(String subject) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_JOB_SEEKER"))
                .jwt(j -> j.subject(subject));
    }

    private RequestPostProcessor adminToken(UUID id) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(j -> j.subject(id.toString()));
    }

    @Test
    void anonymousCallersAreUnauthorized() throws Exception {
        mvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/applications").contentType("application/json").content("{\"jobId\":\"" + jobId + "\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void administratorsCannotUseJobSeekerApplicationEndpoints() throws Exception {
        mvc.perform(get("/api/applications").with(adminToken(userId)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/applications").with(adminToken(userId))
                        .contentType("application/json")
                        .content("{\"jobId\":\"" + jobId + "\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void employersCannotCreateJobSeekerApplications() throws Exception {
        mvc.perform(post("/api/applications").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYER"))
                        .jwt(j -> j.subject(userId.toString())))
                        .contentType("application/json")
                        .content("{\"jobId\":\"" + jobId + "\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void applyUsesTheTokenSubjectAndIgnoresAnyApplicantInTheBody() throws Exception {
        when(service.apply(userId, jobId)).thenReturn(sample());

        mvc.perform(post("/api/applications").with(seekerToken(userId))
                        .contentType("application/json")
                        .content("{\"jobId\":\"" + jobId + "\",\"jobSeekerId\":\"" + UUID.randomUUID()
                                + "\",\"id\":\"" + UUID.randomUUID()
                                + "\",\"status\":\"REJECTED\",\"submittedAt\":\"2000-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()));
        org.mockito.Mockito.verify(service).apply(userId, jobId);
    }

    @Test
    void duplicateAndClosedJobsAreConflicts() throws Exception {
        when(service.apply(eq(userId), any()))
                .thenThrow(new ApplicationFailure(ApplicationFailure.Code.DUPLICATE_APPLICATION))
                .thenThrow(new ApplicationFailure(ApplicationFailure.Code.JOB_NOT_ACCEPTING_APPLICATIONS));
        var token = seekerToken(userId);
        String body = "{\"jobId\":\"" + jobId + "\"}";

        mvc.perform(post("/api/applications").with(token).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_APPLICATION"));
        mvc.perform(post("/api/applications").with(token).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOB_NOT_ACCEPTING_APPLICATIONS"));
    }

    @Test
    void invalidBodyIsBadRequest() throws Exception {
        var token = seekerToken(userId);
        mvc.perform(post("/api/applications").with(token).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/applications").with(token).contentType("application/json").content("{\"jobId\":\"nope\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/applications").with(token).contentType("application/json").content("{\"jobId\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void nonUuidSubjectIsForbidden() throws Exception {
        mvc.perform(get("/api/applications").with(seekerSubject("not-a-uuid")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void listsAndReadsOwnApplications() throws Exception {
        var application = sample();
        when(service.listOwn(userId)).thenReturn(List.of(application));
        when(service.getOwn(userId, application.id())).thenReturn(application);
        var token = seekerToken(userId);

        mvc.perform(get("/api/applications").with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(application.id().toString()));
        mvc.perform(get("/api/applications/" + application.id()).with(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void unknownApplicationIsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getOwn(userId, id)).thenThrow(new ApplicationFailure(ApplicationFailure.Code.NOT_FOUND));

        mvc.perform(get("/api/applications/" + id).with(seekerToken(userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void employerOperationsStayClosedUntilTheirOwnerOpensThem() throws Exception {
        var token = jwt().jwt(j -> j.subject(userId.toString()));
        UUID id = UUID.randomUUID();
        mvc.perform(patch("/api/applications/" + id + "/status").with(token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/applications/" + id).with(token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/applications/" + id + "/anything").with(token)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
