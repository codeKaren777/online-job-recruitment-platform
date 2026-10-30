package com.groupcc2.recruitment.company.controller;

import com.groupcc2.recruitment.company.dto.CompanyRequest;
import com.groupcc2.recruitment.company.dto.CompanyResponse;
import com.groupcc2.recruitment.company.service.CompanyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/companies")
@PreAuthorize("hasRole('EMPLOYER')")
@Tag(name = "Companies", description = "Employer company management")
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {
    private final CompanyService service;
    public CompanyController(CompanyService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a company", description = "Creates a company owned by the authenticated EMPLOYER. Requires a bearer token.", requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(description="Company name and optional company details.",required=true,content=@io.swagger.v3.oas.annotations.media.Content(examples=@ExampleObject(value="{\"name\":\"Acme Technologies\",\"description\":\"Software company\",\"contactEmail\":\"contact@acme.example\",\"contactPhone\":\"+265999000111\",\"location\":\"Blantyre\"}"))))
    @ApiResponses({@ApiResponse(responseCode="201",description="Company created; returns the company details."),@ApiResponse(responseCode="400",description="Request body is malformed or fails validation."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not an active employer."),@ApiResponse(responseCode="409",description="A company with this name already exists for the employer.")})
    public CompanyResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompanyRequest request) {
        return CompanyResponse.from(service.create(userId(jwt), request));
    }

    @GetMapping
    @Operation(summary = "List my companies", description = "Returns companies owned by the authenticated EMPLOYER. Requires a bearer token.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the employer's companies."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not an active employer.")})
    public List<CompanyResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt)).stream().map(CompanyResponse::from).toList();
    }

    @GetMapping("/{companyId}")
    @Operation(summary = "Get my company", description = "Returns the company identified by companyId if owned by the authenticated EMPLOYER. Requires a bearer token.")
    @Parameter(name="companyId",description="UUID of the company.",required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns company details."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not an active employer."),@ApiResponse(responseCode="404",description="Company was not found for this employer.")})
    public CompanyResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID companyId) {
        return CompanyResponse.from(service.get(userId(jwt), companyId));
    }

    @PutMapping("/{companyId}")
    @Operation(summary = "Update my company", description = "Replaces company details for companyId if it is owned by the authenticated EMPLOYER. Requires a bearer token.", requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(description="Company name and optional company details.",required=true,content=@io.swagger.v3.oas.annotations.media.Content(examples=@ExampleObject(value="{\"name\":\"Acme Technologies\",\"description\":\"Software company\",\"contactEmail\":\"contact@acme.example\",\"contactPhone\":\"+265999000111\",\"location\":\"Blantyre\"}"))))
    @Parameter(name="companyId",description="UUID of the company.",required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns updated company details."),@ApiResponse(responseCode="400",description="Request body is malformed or fails validation."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not an active employer."),@ApiResponse(responseCode="404",description="Company was not found for this employer."),@ApiResponse(responseCode="409",description="A company with this name already exists for the employer.")})
    public CompanyResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID companyId,
                                  @Valid @RequestBody CompanyRequest request) {
        return CompanyResponse.from(service.update(userId(jwt), companyId, request));
    }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
