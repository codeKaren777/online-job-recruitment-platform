package com.groupcc2.recruitment.application;

import com.groupcc2.recruitment.application.controller.ApplicationController;
import com.groupcc2.recruitment.application.service.ApplicationService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApplicationController.class, properties = {
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
    @MockitoBean AccountJwtValidator accountJwtValidator;

    @Test
    void applicationCreationSchemaContainsOnlyRequiredJobId() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/applications'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/api/applications'].post.requestBody.content['application/json'].schema.$ref")
                        .value("#/components/schemas/ApplyRequest"))
                .andExpect(jsonPath("$.paths['/api/applications'].post.requestBody.content['application/json'].examples['Valid application'].value.jobId")
                        .value("7e2778ac-26fd-4a2d-b318-8e06e315a082"))
                .andExpect(jsonPath("$.paths['/api/applications'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['503']").exists())
                .andExpect(jsonPath("$.components.schemas.ApplyRequest.properties.jobId.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ApplyRequest.properties.jobId.format").value("uuid"))
                .andExpect(jsonPath("$.components.schemas.ApplyRequest.required[0]").value("jobId"))
                .andExpect(jsonPath("$.components.schemas.ApplyRequest.properties.jobSeekerId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ApplyRequest.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ApplyRequest.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ApplyRequest.properties.submittedAt").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ApplyRequest.properties.updatedAt").doesNotExist());
    }
}
