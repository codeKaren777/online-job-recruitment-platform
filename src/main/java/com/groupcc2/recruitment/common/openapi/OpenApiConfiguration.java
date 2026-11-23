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
import io.swagger.v3.oas.models.media.Schema;
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
                        new io.swagger.v3.oas.models.tags.Tag().name("Users")
                                .description("User accounts, employer upgrade requests, and administrator account operations"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Auth")
                                .description("Registration, login, password changes, and password recovery"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Company")
                                .description("Manage companies owned by the authenticated employer"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Job")
                                .description("Browse and manage job postings"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Application")
                                .description("Submit applications and review applications for employer-owned jobs"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Shortlisting")
                                .description("Shortlist applications and access immutable retained candidate history"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Profile")
                                .description("Manage job seeker profiles and private documents"),
                        new io.swagger.v3.oas.models.tags.Tag().name("Health").description("Application health")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Send a signed access token as: Authorization: Bearer <token>"))
                        .addSchemas("ApiFieldError", new Schema<>().type("object")
                                .addProperty("field", new Schema<String>().type("string"))
                                .addProperty("message", new Schema<String>().type("string")))
                        .addSchemas("ApiErrorResponse", new Schema<>().type("object")
                                .addProperty("timestamp", new Schema<String>().type("string").format("date-time"))
                                .addProperty("status", new Schema<Integer>().type("integer").format("int32"))
                                .addProperty("code", new Schema<String>().type("string"))
                                .addProperty("message", new Schema<String>().type("string"))
                                .addProperty("path", new Schema<String>().type("string"))
                                .addProperty("correlationId", new Schema<String>().type("string"))
                                .addProperty("fieldErrors", new Schema<>().type("array")
                                        .items(new Schema<>().$ref("#/components/schemas/ApiFieldError"))))
                        .addResponses("BadRequest", errorResponse("Invalid request; see the shared error schema."))
                        .addResponses("Unauthorized", errorResponse("Authentication is required or credentials are invalid."))
                        .addResponses("Forbidden", errorResponse("The caller is not permitted."))
                        .addResponses("NotFound", errorResponse("The requested resource was not found."))
                        .addResponses("Conflict", errorResponse("The request conflicts with existing state."))
                        .addResponses("TooManyRequests", errorResponse("The request rate limit was exceeded."))
                        .addResponses("InternalServerError", errorResponse("An unexpected error occurred.")));
    }

    private static ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
    }

    @Bean
    OpenApiCustomizer sharedErrorResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            ApiResponses responses = operation.getResponses();
            addIfMissing(responses, "400", "BadRequest");
            addIfMissing(responses, "401", "Unauthorized");
            addIfMissing(responses, "403", "Forbidden");
            addIfMissing(responses, "404", "NotFound");
            addIfMissing(responses, "409", "Conflict");
            addIfMissing(responses, "429", "TooManyRequests");
            addIfMissing(responses, "500", "InternalServerError");
        }));
    }

    private static void addIfMissing(ApiResponses responses, String status, String component) {
        ApiResponse existing = responses.get(status);
        if (existing == null) {
            responses.addApiResponse(status, new ApiResponse().$ref("#/components/responses/" + component));
        } else {
            existing.setContent(new Content().addMediaType("application/json", new MediaType()
                    .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
        }
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
