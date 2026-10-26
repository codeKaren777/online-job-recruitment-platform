package com.groupcc2.recruitment.company.controller;

import com.groupcc2.recruitment.company.dto.CompanyRequest;
import com.groupcc2.recruitment.company.dto.CompanyResponse;
import com.groupcc2.recruitment.company.service.CompanyService;
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
@RequestMapping("/api/companies")
@PreAuthorize("hasRole('EMPLOYER')")
@Tag(name = "Companies", description = "Employer company management")
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {
    private final CompanyService service;
    public CompanyController(CompanyService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompanyRequest request) {
        return CompanyResponse.from(service.create(userId(jwt), request));
    }

    @GetMapping
    public List<CompanyResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt)).stream().map(CompanyResponse::from).toList();
    }

    @GetMapping("/{companyId}")
    public CompanyResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID companyId) {
        return CompanyResponse.from(service.get(userId(jwt), companyId));
    }

    @PutMapping("/{companyId}")
    public CompanyResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID companyId,
                                  @Valid @RequestBody CompanyRequest request) {
        return CompanyResponse.from(service.update(userId(jwt), companyId, request));
    }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
