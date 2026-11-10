package com.groupcc2.recruitment.users.employer.controller;

import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeRequestBody;
import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeResponse;
import com.groupcc2.recruitment.users.employer.dto.EmployerVerificationMultipartRequest;
import com.groupcc2.recruitment.users.employer.service.EmployerUpgradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;

@RestController
@RequestMapping({"/api/employer-requests", "/api/employer-requests/"})
@PreAuthorize("hasAnyRole('JOB_SEEKER', 'EMPLOYER')")
@Tag(name = "Users", description = "User accounts, employer upgrade requests, and administrator account operations")
@SecurityRequirement(name = "bearerAuth")
public class EmployerUpgradeController {
    private final EmployerUpgradeService service;

    public EmployerUpgradeController(EmployerUpgradeService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit an employer verification application",
            description = "Submits company details as JSON in the application multipart part and a required PDF registration document in the document part. Requires a JOB_SEEKER bearer token.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @io.swagger.v3.oas.annotations.media.Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = EmployerVerificationMultipartRequest.class))))
    @ApiResponses({@ApiResponse(responseCode="201",description="Request submitted; returns request details."),@ApiResponse(responseCode="400",description="Multipart parts are malformed or fail validation."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The account is not permitted to submit this request."),@ApiResponse(responseCode="409",description="An upgrade request cannot be submitted in the account's current state."),@ApiResponse(responseCode="413",description="Registration document exceeds 5 MiB."),@ApiResponse(responseCode="503",description="Private document storage is unavailable.")})
    public EmployerUpgradeResponse submit(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("application") EmployerUpgradeRequestBody body,
            @RequestPart("document") MultipartFile document) {
        return EmployerUpgradeResponse.from(service.submit(userId(jwt), body, document));
    }

    @GetMapping
    @Operation(summary = "List my employer upgrade requests", description = "Returns upgrade requests submitted by the authenticated user. Requires a bearer token.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the caller's upgrade requests."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The account is not permitted to view requests.")})
    public List<EmployerUpgradeResponse> own(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return service.ownRequests(userId(jwt)).stream().map(EmployerUpgradeResponse::from).toList();
    }

    @GetMapping("/{requestId}")
    @Operation(summary = "Get my employer verification submission", description = "Returns only the authenticated applicant's submission and its status.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Submission status and details."),
            @ApiResponse(responseCode="404",description="Submission not found for this applicant."),
            @ApiResponse(responseCode="403",description="JOB_SEEKER or EMPLOYER role required.")})
    public EmployerUpgradeResponse ownOne(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        return EmployerUpgradeResponse.from(service.ownRequest(userId(jwt), requestId));
    }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
