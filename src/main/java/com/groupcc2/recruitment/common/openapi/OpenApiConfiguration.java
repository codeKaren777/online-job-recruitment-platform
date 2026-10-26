package com.groupcc2.recruitment.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI recruitmentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Group CC2 Recruitment API")
                        .description("REST API for the Online Job Recruitment Platform. "
                                + "Operations requiring authentication use an Authorization header "
                                + "with a Bearer JWT. Public registration cannot create ADMIN accounts.")
                        .version("1.0.0")
                        .contact(new Contact().name("Group CC2")))
                .tags(List.of(
                        new io.swagger.v3.oas.models.tags.Tag().name("Users").description("User registration and identity"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Authentication").description("Login and token lifecycle"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Profiles").description("Profiles and private documents"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Jobs").description("Job postings"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Applications").description("Job applications"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Health").description("Application health")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Send a signed access token as: Authorization: Bearer <token>"))
                        .addResponses("BadRequest", new ApiResponse().description("The request is invalid"))
                        .addResponses("Unauthorized", new ApiResponse().description("A valid bearer token is required"))
                        .addResponses("Forbidden", new ApiResponse().description("The caller is not permitted"))
                        .addResponses("NotFound", new ApiResponse().description("The requested resource was not found"))
                        .addResponses("Conflict", new ApiResponse().description("The request conflicts with existing state")));
    }

    /** Actuator endpoints are not controller methods, so publish only the deliberately public health contract. */
    @Bean
    OpenApiCustomizer healthEndpointDocumentation() {
        return openApi -> {
            Schema<?> healthResponse = new Schema<>().type("object").addProperty(
                    "status", new Schema<String>().type("string").example("UP"));
            openApi.getComponents().addSchemas("HealthResponse", healthResponse);

            ApiResponse success = new ApiResponse()
                    .description("Application health is UP")
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/HealthResponse"))));
            Operation operation = new Operation()
                    .summary("Check application health")
                    .description("Public readiness check. Component details are disabled.")
                    .addTagsItem("Health")
                    .responses(new ApiResponses().addApiResponse("200", success));
            operation.setSecurity(java.util.List.of());
            openApi.getPaths().addPathItem("/actuator/health", new PathItem().get(operation));
        };
    }
}
