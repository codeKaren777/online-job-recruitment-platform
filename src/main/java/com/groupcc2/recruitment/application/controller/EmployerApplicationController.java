package com.groupcc2.recruitment.application.controller;

import com.groupcc2.recruitment.application.dto.ApplicationResponse;
import com.groupcc2.recruitment.application.dto.ApplicationStatusRequest;
import com.groupcc2.recruitment.application.service.ApplicationFailure;
import com.groupcc2.recruitment.application.service.ApplicationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Applications", description = "Application submission and access-controlled review")
@SecurityRequirement(name = "bearerAuth")
public class EmployerApplicationController {
    private final ApplicationService service;

    public EmployerApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApplicationResponse> applications(@AuthenticationPrincipal Jwt jwt,
                                                   @org.springframework.web.bind.annotation.RequestParam(required = false) UUID jobId,
                                                   @org.springframework.web.bind.annotation.RequestParam(required = false)
                                                   com.groupcc2.recruitment.application.entity.ApplicationStatus status) {
        return service.listForEmployer(userId(jwt), jobId, status);
    }

    @GetMapping("/{applicationId}")
    public ApplicationResponse application(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID applicationId) {
        return service.getForEmployer(userId(jwt), applicationId);
    }

    @PatchMapping("/{applicationId}/status")
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
