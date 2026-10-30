package com.groupcc2.recruitment.users.employer.controller;

import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeRequestBody;
import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeResponse;
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

@RestController
@RequestMapping("/api/employer-requests")
@PreAuthorize("hasAnyRole('JOB_SEEKER', 'EMPLOYER')")
@Tag(name = "Users", description = "User accounts, employer upgrade requests, and administrator account operations")
@SecurityRequirement(name = "bearerAuth")
public class EmployerUpgradeController {
    private final EmployerUpgradeService service;

    public EmployerUpgradeController(EmployerUpgradeService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit an employer upgrade request", description = "Submits a request for employer access for administrator review. Requires a bearer token.",requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(description="Company and contact details for the upgrade request.",required=true,content=@io.swagger.v3.oas.annotations.media.Content(examples=@ExampleObject(value="{\"companyName\":\"Acme Technologies\",\"businessDescription\":\"Recruitment technology company\",\"contactEmail\":\"contact@acme.example\",\"contactPhone\":\"+265999000111\",\"location\":\"Blantyre\"}"))))
    @ApiResponses({@ApiResponse(responseCode="201",description="Request submitted; returns request details."),@ApiResponse(responseCode="400",description="Request body is malformed or fails validation."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The account is not permitted to submit this request."),@ApiResponse(responseCode="409",description="An upgrade request cannot be submitted in the account's current state.")})
    public EmployerUpgradeResponse submit(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody EmployerUpgradeRequestBody body) {
        return EmployerUpgradeResponse.from(service.submit(userId(jwt), body));
    }

    @GetMapping
    @Operation(summary = "List my employer upgrade requests", description = "Returns upgrade requests submitted by the authenticated user. Requires a bearer token.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the caller's upgrade requests."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The account is not permitted to view requests.")})
    public List<EmployerUpgradeResponse> own(@AuthenticationPrincipal Jwt jwt) {
        return service.ownRequests(userId(jwt)).stream().map(EmployerUpgradeResponse::from).toList();
    }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
