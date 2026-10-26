package com.groupcc2.recruitment.users.employer.controller;

import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeRequestBody;
import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeResponse;
import com.groupcc2.recruitment.users.employer.service.EmployerUpgradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Employer Requests", description = "Requests to become an employer")
@SecurityRequirement(name = "bearerAuth")
public class EmployerUpgradeController {
    private final EmployerUpgradeService service;

    public EmployerUpgradeController(EmployerUpgradeService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit an employer upgrade request")
    public EmployerUpgradeResponse submit(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody EmployerUpgradeRequestBody body) {
        return EmployerUpgradeResponse.from(service.submit(userId(jwt), body));
    }

    @GetMapping
    @Operation(summary = "List my employer upgrade requests")
    public List<EmployerUpgradeResponse> own(@AuthenticationPrincipal Jwt jwt) {
        return service.ownRequests(userId(jwt)).stream().map(EmployerUpgradeResponse::from).toList();
    }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
