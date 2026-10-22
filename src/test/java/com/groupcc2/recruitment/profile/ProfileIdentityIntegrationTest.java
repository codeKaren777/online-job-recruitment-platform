package com.groupcc2.recruitment.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.groupcc2.recruitment.auth.service.JwtTokenService;
import com.groupcc2.recruitment.common.exception.ProfileExceptionHandler;
import com.groupcc2.recruitment.profile.controller.ProfileController;
import com.groupcc2.recruitment.profile.entity.Document;
import com.groupcc2.recruitment.profile.entity.Profile;
import com.groupcc2.recruitment.profile.repository.DocumentRepository;
import com.groupcc2.recruitment.profile.repository.ProfileRepository;
import com.groupcc2.recruitment.profile.service.ProfileIdentity;
import com.groupcc2.recruitment.profile.service.ProfileRls;
import com.groupcc2.recruitment.profile.service.ProfileService;
import com.groupcc2.recruitment.profile.service.SecurityContextProfileIdentity;
import com.groupcc2.recruitment.profile.storage.DocumentValidator;
import com.groupcc2.recruitment.profile.storage.PrivateDocumentStorage;
import com.groupcc2.recruitment.profile.dto.ProfileRequest;
import com.groupcc2.recruitment.security.AccountJwtValidator;
import com.groupcc2.recruitment.security.SecurityConfig;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.InactiveAccountException;
import com.groupcc2.recruitment.users.service.UserNotFoundException;
import com.groupcc2.recruitment.users.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@WebMvcTest(controllers = ProfileController.class, properties = {
        "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.security.jwt.issuer=https://issuer.example.test",
        "app.security.jwt.audience=profile-identity-tests"
})
@Import({SecurityConfig.class, ProfileService.class, SecurityContextProfileIdentity.class,
        ProfileExceptionHandler.class, JwtTokenService.class, DocumentValidator.class,
        ProfileIdentityIntegrationTest.TestTransactions.class})
class ProfileIdentityIntegrationTest {
    private static final UUID AUTHENTICATED_USER_ID = UUID.fromString("37e69c88-42f4-4203-9138-cf52172645bb");
    private static final UUID CLIENT_SUPPLIED_USER_ID = UUID.fromString("9c0e6806-c438-4ff7-94c0-bfbe743ffbd0");

    @Autowired MockMvc mvc;
    @Autowired ProfileIdentity profileIdentity;
    @Autowired ProfileService profileService;
    @Autowired JwtTokenService jwtTokenService;

    @MockitoBean UserService users;
    @MockitoBean ProfileRls rls;
    @MockitoBean ProfileRepository profiles;
    @MockitoBean DocumentRepository documents;
    @MockitoBean PrivateDocumentStorage storage;
    @MockitoBean AccountJwtValidator accountJwtValidator;

    @TestConfiguration(proxyBeanMethods = false)
    static class TestTransactions {
        @Bean
        PlatformTransactionManager testTransactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }
    }

    private User activeJobSeeker;

    @BeforeEach
    void setUp() {
        activeJobSeeker = org.mockito.Mockito.mock(User.class);
        when(activeJobSeeker.getRole()).thenReturn(UserRole.JOB_SEEKER);
        when(users.findById(AUTHENTICATED_USER_ID)).thenReturn(activeJobSeeker);
        when(profiles.findById(AUTHENTICATED_USER_ID)).thenReturn(Optional.of(
                new Profile(AUTHENTICATED_USER_ID, new ProfileRequest("Authenticated owner", null, null))));
        when(profiles.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documents.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountJwtValidator.validate(any())).thenReturn(OAuth2TokenValidatorResult.success());
    }

    @Test
    void productionAdapterIsInjectedAndResolvesIdentityFromValidatedBearerJwt() throws Exception {
        assertThat(profileIdentity).isInstanceOf(SecurityContextProfileIdentity.class);
        assertThat(profileService).isNotNull();

        mvc.perform(get("/api/profile").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Authenticated owner"));

        verify(users).findById(AUTHENTICATED_USER_ID);
        verify(users).requireActive(activeJobSeeker);
        verify(rls).establish(AUTHENTICATED_USER_ID, "");
        verify(rls).establish(AUTHENTICATED_USER_ID, UserRole.JOB_SEEKER.name());
        verify(profiles).findById(AUTHENTICATED_USER_ID);
    }

    @Test
    void clientSuppliedUserIdCannotOverrideJwtIdentity() throws Exception {
        mvc.perform(put("/api/profile")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Updated owner","userId":"%s"}
                                """.formatted(CLIENT_SUPPLIED_USER_ID)))
                .andExpect(status().isOk());

        verify(users).findById(AUTHENTICATED_USER_ID);
        verify(profiles).findById(AUTHENTICATED_USER_ID);
        org.mockito.Mockito.verify(profiles, org.mockito.Mockito.never()).findById(CLIENT_SUPPLIED_USER_ID);
    }

    @Test
    void missingUserIsDeniedThroughUsersServiceContract() throws Exception {
        when(users.findById(AUTHENTICATED_USER_ID)).thenThrow(new UserNotFoundException());

        mvc.perform(get("/api/profile").header("Authorization", bearerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(profiles);
    }

    @Test
    void inactiveAccountIsDeniedBeforeProfileAccess() throws Exception {
        doThrow(new InactiveAccountException()).when(users).requireActive(activeJobSeeker);

        mvc.perform(get("/api/profile").header("Authorization", bearerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(profiles);
    }

    @Test
    void usersServiceFailureFailsClosedWithoutLeakingDetails() throws Exception {
        when(users.findById(AUTHENTICATED_USER_ID))
                .thenThrow(new IllegalStateException("private database diagnostic"));

        mvc.perform(get("/api/profile").header("Authorization", bearerToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROFILE_UNAVAILABLE"));
        verifyNoInteractions(profiles);
    }

    @Test
    void missingAuthenticationIsRejectedBeforeProfileService() throws Exception {
        mvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users, profiles);
    }

    @Test
    void validPdfUploadReturnsCreatedAndOnlySafeMetadata() throws Exception {
        byte[] pdf = "%PDF-1.7\n1 0 obj <<>> endobj\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        mvc.perform(multipart("/api/profile/documents")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", pdf))
                        .header("Authorization", bearerToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.url").doesNotExist());

        org.mockito.Mockito.verify(storage).upload(org.mockito.ArgumentMatchers.matches("documents/[a-f0-9-]{36}"),
                any(), org.mockito.ArgumentMatchers.eq("application/pdf"));
        verify(documents).saveAndFlush(any());
    }

    @Test
    void oversizedPdfReturnsPayloadTooLarge() throws Exception {
        byte[] oversized = new byte[(int) DocumentValidator.MAX_BYTES + 1];

        mvc.perform(multipart("/api/profile/documents")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", oversized))
                        .header("Authorization", bearerToken()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void malformedPdfIsRejectedBeforeObjectStorage() throws Exception {
        mvc.perform(multipart("/api/profile/documents")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf", "not a PDF".getBytes()))
                        .header("Authorization", bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));

        org.mockito.Mockito.verifyNoInteractions(storage);
        org.mockito.Mockito.verifyNoInteractions(documents);
    }

    @Test
    void ownerDownloadReturnsConfiguredShortLivedSignedUrl() throws Exception {
        UUID documentId = UUID.fromString("2cb204dd-6605-468a-b4b7-7873a80a9459");
        Document document = new Document(AUTHENTICATED_USER_ID, "documents/private-key", "cv.pdf", "application/pdf", 20);
        java.net.URI signedUrl = java.net.URI.create("http://localhost:9000/recruitment-documents/documents/private-key?X-Amz-Expires=120");
        when(documents.findByIdAndProfileUserId(documentId, AUTHENTICATED_USER_ID)).thenReturn(Optional.of(document));
        when(storage.signGet("documents/private-key", java.time.Duration.ofSeconds(120))).thenReturn(signedUrl);

        mvc.perform(get("/api/profile/documents/{documentId}/download", documentId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(signedUrl.toString()))
                .andExpect(jsonPath("$.expiresAt").exists());

        verify(documents).findByIdAndProfileUserId(documentId, AUTHENTICATED_USER_ID);
        verify(storage).signGet("documents/private-key", java.time.Duration.ofSeconds(120));
    }

    @Test
    void foreignDocumentDownloadDoesNotCallStorageSigner() throws Exception {
        UUID foreignDocumentId = UUID.randomUUID();
        when(documents.findByIdAndProfileUserId(foreignDocumentId, AUTHENTICATED_USER_ID))
                .thenReturn(Optional.empty());

        mvc.perform(get("/api/profile/documents/{documentId}/download", foreignDocumentId)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verifyNoInteractions(storage);
    }

    private String bearerToken() {
        return "Bearer " + jwtTokenService.generateAccessToken(AUTHENTICATED_USER_ID, UserRole.JOB_SEEKER);
    }
}
