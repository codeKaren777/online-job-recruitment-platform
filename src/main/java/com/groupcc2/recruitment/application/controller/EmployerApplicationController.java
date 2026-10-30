package com.groupcc2.recruitment.application.controller;

import com.groupcc2.recruitment.application.dto.ApplicationResponse;
import com.groupcc2.recruitment.application.dto.ApplicationStatusRequest;
import com.groupcc2.recruitment.application.service.ApplicationFailure;
import com.groupcc2.recruitment.application.service.ApplicationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

/** Employer reads are scoped to jobs owned by the authenticated employer. */
@RestController
@RequestMapping("/api/employer/applications")
@Tag(name = "Application", description = "Submit applications and review applications for employer-owned jobs")
@SecurityRequirement(name = "bearerAuth")
public class EmployerApplicationController {
    private final ApplicationService service;

    public EmployerApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List applications for my jobs", description = "Returns applications for jobs owned by the authenticated EMPLOYER. Requires a bearer token.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns matching applications."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to review applications."),@ApiResponse(responseCode="400",description="A query parameter has an invalid format."),@ApiResponse(responseCode="503",description="Application service is unavailable.")})
    public List<ApplicationResponse> applications(@AuthenticationPrincipal Jwt jwt,
                                                   @Parameter(description="Optional UUID to filter applications by job.") @org.springframework.web.bind.annotation.RequestParam(required = false) UUID jobId,
                                                   @Parameter(description="Optional application status filter.") @org.springframework.web.bind.annotation.RequestParam(required = false)
                                                   com.groupcc2.recruitment.application.entity.ApplicationStatus status) {
        return service.listForEmployer(userId(jwt), jobId, status);
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "Get an application for my jobs", description = "Returns the application identified by applicationId when it belongs to one of the authenticated employer's jobs. Requires an EMPLOYER bearer token.")
    @Parameter(name="applicationId", description="UUID of the application.", required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the application."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to access this application."),@ApiResponse(responseCode="404",description="Application was not found."),@ApiResponse(responseCode="503",description="Application service is unavailable.")})
    public ApplicationResponse application(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID applicationId) {
        return service.getForEmployer(userId(jwt), applicationId);
    }

    @PatchMapping("/{applicationId}/status")
    @Operation(summary = "Update an application status", description = "Sets the status of an application belonging to an authenticated employer's job. Requires an EMPLOYER bearer token.", requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(description="New application status.",required=true,content=@io.swagger.v3.oas.annotations.media.Content(examples=@ExampleObject(value="{\"status\":\"SHORTLISTED\"}"))))
    @Parameter(name="applicationId", description="UUID of the application.", required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the updated application."),@ApiResponse(responseCode="400",description="Request body is malformed or status is invalid."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to update this application."),@ApiResponse(responseCode="404",description="Application was not found."),@ApiResponse(responseCode="409",description="The requested status transition is not allowed."),@ApiResponse(responseCode="503",description="Application service is unavailable.")})
    public ApplicationResponse changeStatus(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID applicationId,
                                             @Valid @RequestBody ApplicationStatusRequest request) {
        return service.changeStatus(userId(jwt), applicationId, request.status());
    }

    private static UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationFailure(ApplicationFailure.Code.FORBIDDEN);
        }
    }
}
