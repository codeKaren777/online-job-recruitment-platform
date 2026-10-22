package com.groupcc2.recruitment.application.controller;

import java.util.List;
import java.util.UUID;

import com.groupcc2.recruitment.application.dto.ApplicationResponse;
import com.groupcc2.recruitment.application.dto.ApplyRequest;
import com.groupcc2.recruitment.application.service.ApplicationFailure;
import com.groupcc2.recruitment.application.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the job seeker's applications. The caller is the JWT subject (a user UUID);
 * no request carries an applicant id or role. Role and account state are checked in the service.
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ApplyRequest request) {
        return service.apply(userId(jwt), request.jobId());
    }

    @GetMapping
    public List<ApplicationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.listOwn(userId(jwt));
    }

    @GetMapping("/{applicationId}")
    public ApplicationResponse one(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID applicationId) {
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
