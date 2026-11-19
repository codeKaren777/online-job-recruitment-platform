package com.groupcc2.recruitment.application.controller;

import java.util.UUID;

import com.groupcc2.recruitment.application.dto.ShortlistCandidateResponse;
import com.groupcc2.recruitment.application.service.ApplicationFailure;
import com.groupcc2.recruitment.application.service.ShortlistCandidateRetention;
import com.groupcc2.recruitment.job.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employer/shortlisted-candidates")
@Tag(name = "Shortlisted candidates", description = "Immutable candidate history retained when applications are shortlisted")
@SecurityRequirement(name = "bearerAuth")
public class ShortlistCandidateController {
    private static final int MAX_PAGE_SIZE = 50;
    private final ObjectProvider<ShortlistCandidateRetention> retention;

    public ShortlistCandidateController(ObjectProvider<ShortlistCandidateRetention> retention) {
        this.retention = retention;
    }

    @GetMapping
    @Operation(summary = "List retained shortlisted candidates",
            description = "Returns retained shortlist records for jobs owned by the authenticated employer. Records remain available after application withdrawal, removal from active application views, or job expiry.")
    public PageResponse<ShortlistCandidateResponse> list(
            @AuthenticationPrincipal @Parameter(hidden = true) Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID employerId = userId(jwt);
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageResponse.from(service().list(employerId,
                PageRequest.of(boundedPage, boundedSize,
                        Sort.by(Sort.Direction.DESC, "shortlistedAt"))).map(ShortlistCandidateResponse::from));
    }

    @GetMapping("/{candidateId}")
    @Operation(summary = "Get a retained shortlisted candidate",
            description = "Returns one immutable shortlist record when it belongs to a job owned by the authenticated employer.")
    public ShortlistCandidateResponse get(
            @AuthenticationPrincipal @Parameter(hidden = true) Jwt jwt,
            @PathVariable UUID candidateId) {
        return ShortlistCandidateResponse.from(service().get(userId(jwt), candidateId));
    }

    private ShortlistCandidateRetention service() {
        ShortlistCandidateRetention service = retention.getIfAvailable();
        if (service == null) {
            throw new ApplicationFailure(ApplicationFailure.Code.INTEGRATION_UNAVAILABLE);
        }
        return service;
    }

    private static UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationFailure(ApplicationFailure.Code.FORBIDDEN);
        }
    }
}
