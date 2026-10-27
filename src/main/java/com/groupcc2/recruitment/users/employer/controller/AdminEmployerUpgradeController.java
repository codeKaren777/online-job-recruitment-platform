package com.groupcc2.recruitment.users.employer.controller;

import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeDecision;
import com.groupcc2.recruitment.users.employer.dto.EmployerUpgradeResponse;
import com.groupcc2.recruitment.users.employer.service.EmployerUpgradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Administration", description = "Administrative account management")
@SecurityRequirement(name = "bearerAuth")
public class AdminEmployerUpgradeController {
    private final EmployerUpgradeService service;

    public AdminEmployerUpgradeController(EmployerUpgradeService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "List pending employer upgrade requests")
    public List<EmployerUpgradeResponse> pending(@AuthenticationPrincipal Jwt jwt) {
        return service.pending(userId(jwt)).stream().map(EmployerUpgradeResponse::from).toList();
    }

    @PatchMapping("/{requestId}/approve")
    @Operation(summary = "Approve an employer upgrade request")
    public EmployerUpgradeResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        return EmployerUpgradeResponse.from(service.approve(userId(jwt), requestId));
    }

    @PatchMapping("/{requestId}/reject")
    @Operation(summary = "Reject an employer upgrade request")
    public EmployerUpgradeResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId,
                                           @Valid @RequestBody EmployerUpgradeDecision decision) {
        return EmployerUpgradeResponse.from(service.reject(userId(jwt), requestId, decision.reason()));
    }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
