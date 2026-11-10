package com.groupcc2.recruitment.application;

import com.groupcc2.recruitment.application.controller.ApplicationController;
import com.groupcc2.recruitment.application.controller.EmployerApplicationController;
import com.groupcc2.recruitment.application.service.ApplicationService;
import com.groupcc2.recruitment.users.employer.controller.EmployerUpgradeController;
import com.groupcc2.recruitment.users.employer.controller.AdminEmployerUpgradeController;
import com.groupcc2.recruitment.users.employer.service.EmployerUpgradeService;
import com.groupcc2.recruitment.common.openapi.OpenApiConfiguration;
import com.groupcc2.recruitment.security.AccountJwtValidator;
import com.groupcc2.recruitment.security.SecurityConfig;
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
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(controllers = {ApplicationController.class, EmployerApplicationController.class,
        EmployerUpgradeController.class, AdminEmployerUpgradeController.class}, properties = {
        "app.security.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.openapi.enabled=true",
        "app.openapi.public-in-local=true",
        "springdoc.api-docs.enabled=true"
})
@ActiveProfiles("local")
@Import({SecurityConfig.class, OpenApiConfiguration.class})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
class ApplicationOpenApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ApplicationService applicationService;
    @MockitoBean EmployerUpgradeService employerUpgradeService;
    @MockitoBean AccountJwtValidator accountJwtValidator;

    @Test
    void yamlSpecificationIsAvailableInLocalProfile() throws Exception {
        mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openapi:")))
                .andExpect(content().string(containsString("/api/applications:")));
    }

    @Test
    void applicationCreationSchemaDocumentsRequiredMultipartCv() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/applications'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/api/applications'].post.requestBody.content['multipart/form-data'].schema.$ref")
                        .value("#/components/schemas/ApplicationSubmissionRequest"))
                .andExpect(jsonPath("$.paths['/api/applications'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['413']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['503']").exists())
                .andExpect(jsonPath("$.components.schemas.ApplicationSubmissionRequest.properties.jobId.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApplicationSubmissionRequest.properties.jobId.format").value("uuid"))
                .andExpect(jsonPath("$.components.schemas.ApplicationSubmissionRequest.properties.cv.format").value("binary"))
                .andExpect(jsonPath("$.components.schemas.ApplicationSubmissionRequest.required").isArray())
                .andExpect(jsonPath("$.components.schemas.ApplicationResponse.properties.cv.$ref").value("#/components/schemas/DocumentResponse"))
                .andExpect(jsonPath("$.components.schemas.ApplicationResponse.properties.applicantDisplayName").exists())
                .andExpect(jsonPath("$.paths['/api/employer/applications'].get.responses['200'].content['*/*'].schema.items.$ref")
                        .value("#/components/schemas/ApplicationResponse"))
                .andExpect(jsonPath("$.paths['/api/employer/applications/{applicationId}/cv'].get.responses['200'].content['*/*'].schema.$ref")
                        .value("#/components/schemas/DownloadResponse"));
    }

    @Test
    void employerVerificationDocumentsMultipartPartsAndAdminReviewRoutes() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/employer-requests'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/api/employer-requests'].post.requestBody.content['multipart/form-data'].schema.$ref")
                        .value("#/components/schemas/EmployerVerificationMultipartRequest"))
                .andExpect(jsonPath("$.components.schemas.EmployerVerificationMultipartRequest.properties.document.format")
                        .value("binary"))
                .andExpect(jsonPath("$.components.schemas.EmployerVerificationMultipartRequest.required").isArray())
                .andExpect(jsonPath("$.paths['/api/employer-requests/{requestId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/employer-requests/{requestId}/document'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/employer-requests/{requestId}/approve'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/admin/employer-requests/{requestId}/reject'].patch").exists());
    }
}
