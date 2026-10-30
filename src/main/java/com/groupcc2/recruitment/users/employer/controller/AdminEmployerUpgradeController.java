package com.groupcc2.recruitment.users.employer.controller;

import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeDecision;
import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeResponse;
import com.groupcc2.recruitment.users.employer.service.EmployerUpgradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/employer-requests")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "User accounts, employer upgrade requests, and administrator account operations")
@SecurityRequirement(name = "bearerAuth")
public class AdminEmployerUpgradeController {
    private final EmployerUpgradeService service;

    public AdminEmployerUpgradeController(EmployerUpgradeService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "List pending employer upgrade requests", description = "Returns upgrade requests awaiting an administrator decision. Requires an ADMIN bearer token.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns pending upgrade requests."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller must have the ADMIN role.")})
    public List<EmployerUpgradeResponse> pending(@AuthenticationPrincipal Jwt jwt) {
        return service.pending(userId(jwt)).stream().map(EmployerUpgradeResponse::from).toList();
    }

    @PatchMapping("/{requestId}/approve")
    @Operation(summary = "Approve an employer upgrade request", description = "Approves requestId and upgrades the applicant to employer. Requires an ADMIN bearer token.")
    @Parameter(name="requestId",description="UUID of the employer upgrade request.",required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the approved request."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller must have the ADMIN role."),@ApiResponse(responseCode="404",description="Request was not found.")})
    public EmployerUpgradeResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        return EmployerUpgradeResponse.from(service.approve(userId(jwt), requestId));
    }

    @PatchMapping("/{requestId}/reject")
    @Operation(summary = "Reject an employer upgrade request", description = "Rejects requestId with a reason. Requires an ADMIN bearer token.",requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(description="Reason for rejecting the request.",required=true,content=@io.swagger.v3.oas.annotations.media.Content(examples=@ExampleObject(value="{\"reason\":\"Business details could not be verified.\"}"))))
    @Parameter(name="requestId",description="UUID of the employer upgrade request.",required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the rejected request."),@ApiResponse(responseCode="400",description="Request body is malformed or fails validation."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller must have the ADMIN role."),@ApiResponse(responseCode="404",description="Request was not found.")})
    public EmployerUpgradeResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId,
                                           @Valid @RequestBody EmployerUpgradeDecision decision) {
        return EmployerUpgradeResponse.from(service.reject(userId(jwt), requestId, decision.reason()));
    }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
