package com.groupcc2.recruitment.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.groupcc2.recruitment.profile.controller.ProfileController;
import com.groupcc2.recruitment.profile.dto.DocumentResponse;
import com.groupcc2.recruitment.profile.dto.ProfileRequest;
import com.groupcc2.recruitment.profile.dto.ProfileResponse;
import com.groupcc2.recruitment.profile.service.ProfileService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;

@WebMvcTest(controllers = ProfileController.class,
        properties = "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import(SecurityConfig.class)
class ProfileControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfileService profileService;
    @MockitoBean AccountJwtValidator accountJwtValidator;

    @Test
    void everyProfileRouteRejectsAnonymousRequests() throws Exception {
        assertRoutes(null, status().isUnauthorized());
        verifyNoInteractions(profileService);
    }

    @Test
    void everyProfileRouteRejectsEmployersAndAdministrators() throws Exception {
        for (String role : List.of("EMPLOYER", "ADMIN")) {
            assertRoutes(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)),
                    status().isForbidden());
        }
        verifyNoInteractions(profileService);
    }

    @Test
    void jobSeekersCanUseAllOwnProfileRoutes() throws Exception {
        UUID documentId = UUID.randomUUID();
        Instant now = Instant.now();
        when(profileService.readOwn()).thenReturn(new ProfileResponse("Name", null, null, now, now));
        when(profileService.upsert(new ProfileRequest("Name", null, null)))
                .thenReturn(new ProfileResponse("Name", null, null, now, now));
        when(profileService.upload(any()))
                .thenReturn(new DocumentResponse(documentId, "cv.pdf", "application/pdf", 20, now));
        when(profileService.listOwn()).thenReturn(List.of());

        RequestPostProcessor token = jwt().jwt(jwt -> jwt
                        .subject(UUID.randomUUID().toString()).claim("role", "JOB_SEEKER"))
                .authorities(new SimpleGrantedAuthority("ROLE_JOB_SEEKER"));

        mvc.perform(get("/api/profile").with(token)).andExpect(status().isOk());
        mvc.perform(put("/api/profile").with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Name\"}"))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/profile/documents")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[]{1}))
                        .with(token))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/profile/documents").with(token)).andExpect(status().isOk());
        mvc.perform(delete("/api/profile/documents/{documentId}", documentId).with(token))
                .andExpect(status().isNoContent());

        verify(profileService).readOwn();
        verify(profileService).upsert(new ProfileRequest("Name", null, null));
        verify(profileService).upload(any());
        verify(profileService).listOwn();
        verify(profileService).deleteOwn(documentId);
    }

    private void assertRoutes(RequestPostProcessor token, ResultMatcher expected) throws Exception {
        mvc.perform(withToken(get("/api/profile"), token)).andExpect(expected);
        mvc.perform(withToken(put("/api/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Name\"}"), token)).andExpect(expected);
        mvc.perform(withToken(multipart("/api/profile/documents")
                .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[]{1})), token))
                .andExpect(expected);
        mvc.perform(withToken(get("/api/profile/documents"), token)).andExpect(expected);
        mvc.perform(withToken(delete("/api/profile/documents/{documentId}", UUID.randomUUID()), token))
                .andExpect(expected);
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request,
                                                     RequestPostProcessor token) {
        return token == null ? request : request.with(token);
    }
}
