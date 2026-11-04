package com.groupcc2.recruitment.application.controller;

import java.util.List;
import java.util.UUID;

import com.groupcc2.recruitment.application.dto.ApplicationResponse;
import com.groupcc2.recruitment.application.dto.ApplicationSubmissionRequest;
import com.groupcc2.recruitment.application.service.ApplicationFailure;
import com.groupcc2.recruitment.application.service.ApplicationService;
import com.groupcc2.recruitment.common.exception.ApplicationExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.validation.Valid;

/**
 * HTTP adapter for the job seeker's applications. The caller is the JWT subject (a user UUID);
 * no request carries an applicant id or role. Role and account state are checked in the service.
 */
@RestController
@RequestMapping("/api/applications")
@Tag(name = "Application", description = "Submit applications and review applications for employer-owned jobs")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {
    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply to a job",
            description = "Creates a PENDING application for the authenticated job seeker. Applicant identity, "
                    + "application ID, status, and timestamps are assigned by the server. Each application requires its own PDF CV (maximum 5 MiB).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Multipart form with a jobId and the PDF CV to submit for this application. The CV is preserved for this application and does not replace profile documents.", required = true,
                    content = @Content(mediaType = "multipart/form-data", schema = @Schema(implementation = ApplicationSubmissionRequest.class))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application created",
                    content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing job ID/CV, PROFILE_INCOMPLETE, or invalid CV",
                    content = @Content(schema = @Schema(implementation = ApplicationExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "CV exceeds the 5 MiB upload limit",
                    content = @Content(schema = @Schema(implementation = ApplicationExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "Only active job seekers may apply",
                    content = @Content(schema = @Schema(implementation = ApplicationExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Job posting does not exist",
                    content = @Content(schema = @Schema(implementation = ApplicationExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Job is not accepting applications or this seeker already applied",
                    content = @Content(schema = @Schema(implementation = ApplicationExceptionHandler.ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Job posting service is unavailable",
                    content = @Content(schema = @Schema(implementation = ApplicationExceptionHandler.ErrorResponse.class)))
    })
    public ApplicationResponse apply(
            @AuthenticationPrincipal @io.swagger.v3.oas.annotations.Parameter(hidden = true) Jwt jwt,
            @org.springframework.web.bind.annotation.ModelAttribute @Valid ApplicationSubmissionRequest request) {
        return service.applyWithCv(userId(jwt), request.getJobId(), request.getCv());
    }

    @GetMapping
    @Operation(summary = "List my applications", description = "Returns applications submitted by the authenticated job seeker. Requires a JOB_SEEKER bearer token.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the caller's applications."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to view applications."),@ApiResponse(responseCode="503",description="Application service is unavailable.")})
    public List<ApplicationResponse> mine(@AuthenticationPrincipal @io.swagger.v3.oas.annotations.Parameter(hidden = true) Jwt jwt) {
        return service.listOwn(userId(jwt));
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "Get my application", description = "Returns the application identified by applicationId if it belongs to the authenticated job seeker. Requires a JOB_SEEKER bearer token.")
    @io.swagger.v3.oas.annotations.Parameter(name="applicationId", description="UUID of the application.", required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the application."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to view this application."),@ApiResponse(responseCode="404",description="Application was not found."),@ApiResponse(responseCode="503",description="Application service is unavailable.")})
    public ApplicationResponse one(@AuthenticationPrincipal @io.swagger.v3.oas.annotations.Parameter(hidden = true) Jwt jwt, @PathVariable UUID applicationId) {
        return service.getOwn(userId(jwt), applicationId);
    }


    private static UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationFailure(ApplicationFailure.Code.FORBIDDEN);
        }
    }
}
